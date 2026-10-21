"""Builds the YouTube Short from VideoTour's frames.

python assemble.py <video_tour dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes summiteer-short.mp4 (the game's own sounds), summiteer-short-silent.mp4 and
summiteer-short-thumbnail.png, 1080x1920. It opens already climbing a cliff with the ice axe, and
its last shot (the climb again, from the ground) leads back into the first, so a replay loops; no
title or end card. The follow line sits over the last second and a half.

Captions are bold white capitals with a black outline straight on the footage, one word in the
goggles' amber (it reads against snow). A small Summiteer badge stays in one place all the way
through. Each scene's cut comes from the moments VideoTour logs (the gusts, the landing, the top),
so a new take needs no new numbers here. If a scene changes in VideoTour, change it here too.
"""
import array, csv, os, random, shutil, subprocess, sys, wave
from PIL import Image, ImageDraw, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
AMBER = (242, 181, 74)
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

W, H, SIZE, STROKE, LEAD = 1080, 1920, 84, 7, 104
NAME = 'summiteer-short'
CAPTION_TOP = 390
CTA_TOP, CTA_FRAMES = 1290, 30
BADGE_BOX = (40, 250, 540, 350)

with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]


def at(scene, kind, nth=0):
    """The frame of the nth logged `kind` in `scene`, or None."""
    hits = [fr for s, fr, k, _ in actions if s == scene and k == kind]
    return hits[nth] if len(hits) > nth else None


def count(scene):
    return len([n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png')])


def wind_cuts():
    """The first gust from just into its warning to just after it lands, then the braced one around its hit."""
    warn, hit, braced = at('s_wind', 'gust_warning'), at('s_wind', 'gust'), at('s_wind', 'braced_gust')
    cuts = []
    if warn is not None and hit is not None:
        cuts.append((warn + 15, hit + 20))
    if braced is not None:
        cuts.append((braced - 15, braced + 15))
    return cuts


def fall_cut():
    landed = at('s_fall', 'landed')
    return [(0, (landed + 15) if landed is not None else count('s_fall'))]


def summit_cut():
    top = at('s_summit', 'on_top')
    n = count('s_summit')
    return [(max(0, top - 15), min(n, top + 55))] if top is not None else [(0, n)]


# Scene, its cuts (first frame, end frame), caption lines ("*word*" in amber).
SCENES = [
    ('s_climb', lambda: [(0, count('s_climb'))], ['NOW YOU CAN', 'CLIMB *CLIFFS*']),
    ('s_wind', wind_cuts, ['THE WIND', '*FIGHTS BACK*']),
    ('s_fall', fall_cut, ['FALL?', '*HOLD ON*']),
    ('s_summit', summit_cut, ['MOUNTAINS', '*WORTH CLIMBING*']),
    ('s_loop', lambda: [(0, count('s_loop'))], None),
]

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


CAPTION = font(FONTS, SIZE)


def caption(lines, top):
    """A transparent frame-sized layer with the caption lines, centred."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, AMBER if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = (W - sum(d.textlength(p, font=CAPTION) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=CAPTION, fill=colour, stroke_width=STROKE, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=CAPTION)
        y += LEAD
    return layer


def badge():
    """A small mod-list style badge: the icon, the name, the loaders and version."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = BADGE_BOX
    d.rounded_rectangle(box, 18, fill=(20, 20, 24, 225))
    side = box[3] - box[1] - 28
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/summiteer/icon.png')).convert('RGBA')
    icon = icon.resize((side, side), Image.NEAREST)
    layer.alpha_composite(icon, (box[0] + 14, box[1] + 14))
    x = box[0] + side + 32
    d.text((x, box[1] + 12), 'Summiteer', font=font(BADGE_FONTS, 40), fill=(255, 255, 255))
    d.text((x, box[1] + 60), 'Fabric · NeoForge · 26.3', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer


def faded(layer, a):
    layer = layer.copy()
    layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
    return layer


BADGE = badge()
CTA = caption(['FOLLOW FOR', 'MORE *MODS*'], CTA_TOP)

work = os.path.join(out, 'work-short')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
# Each kept frame: (scene, source frame) in playback order; the clip index each scene starts at.
kept, starts = [], {}
for scene, cuts, lines in SCENES:
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png'))
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    starts[scene] = len(kept)
    cap = caption(lines, CAPTION_TOP) if lines else None
    frames = [n for first, end in cuts() for n in range(max(0, first), min(end, len(names)))]
    for i, n in enumerate(frames):
        img = Image.open(os.path.join(frames_dir, names[n])).convert('RGBA')
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        img.alpha_composite(BADGE)
        if cap is not None:
            img.alpha_composite(faded(cap, min(1.0, (i + 1) / FADE_IN)))
        if scene == 's_loop' and i >= len(frames) - CTA_FRAMES:
            img.alpha_composite(faded(CTA, min(1.0, (i - (len(frames) - CTA_FRAMES) + 1) / FADE_IN)))
        img.convert('RGB').save(os.path.join(work, '%05d.png' % len(kept)))
        kept.append((scene, n))
total = len(kept) / FPS

silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, '%05d.png'),
    '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-movflags', '+faststart', silent)

# The game's own sounds where they happen: stone steps while climbing and walking up, the gust's
# whoosh at each warning (cut to two seconds, as the mod plays it), snow under the landing.
random.seed(7)
where = {}
for i, (scene, n) in enumerate(kept):
    where.setdefault((scene, n), i)


def cue_at(scene, frame):
    i = where.get((scene, frame))
    return None if i is None else i / FPS


cues = []
for scene in ('s_climb', 's_loop', 's_fall'):
    for (s, n), i in where.items():
        if s == scene and n % 8 == 0 and (scene != 's_fall' or at('s_fall', 'landed') is None or n < at('s_fall', 'landed')):
            cues.append((i / FPS, random.choice(['stone1', 'stone2', 'stone3', 'stone4']), 0.9 + random.random() * 0.2, 0.5, None))
for kind in ('gust_warning', 'braced_gust_warning'):
    f = at('s_wind', kind)
    if f is not None:
        # The warning may sit before the cut: start the whoosh at the first kept frame after it.
        t = next((cue_at('s_wind', g) for g in range(f, f + 60) if cue_at('s_wind', g) is not None), None)
        if t is not None:
            cues.append((t, 'whoosh', 1.4, 0.7, 2.2))
landed = at('s_fall', 'landed')
if landed is not None and cue_at('s_fall', landed) is not None:
    cues.append((cue_at('s_fall', landed), 'snow2', 1.0, 0.9, None))
top = at('s_summit', 'on_top')
for (s, n), i in where.items():
    if s == 's_summit' and top is not None and n < top and n % 7 == 0:
        cues.append((i / FPS, random.choice(['snow1', 'snow3', 'snow4']), 0.9 + random.random() * 0.2, 0.6, None))
cues.sort()

audio = os.path.join(work, 'sound.wav')
# The mix is laid down here in Python, not in one big ffmpeg graph: ffmpeg 9 stalled for good
# partway through the trailer's many-cue amix (2026-10-21). ffmpeg only decodes each sound once.
RATE = 44100
decoded = {}


def pcm(sound):
    """A sound as 16-bit stereo samples at 44.1 kHz (left, right, left, ...)."""
    if sound not in decoded:
        raw = subprocess.run(['ffmpeg', '-nostdin', '-loglevel', 'error', '-i', os.path.join(sounds_dir, sound + '.ogg'),
                              '-f', 's16le', '-ac', '2', '-ar', str(RATE), '-'], check=True, capture_output=True, timeout=60).stdout
        decoded[sound] = array.array('h', raw)
    return decoded[sound]


length_frames = int(round(total * RATE))
mix = array.array('f', bytes(4 * 2 * length_frames))
for t, sound, pitch, volume, length in cues:
    src = pcm(sound)
    frames = len(src) // 2
    if length:
        frames = min(frames, int(length * RATE))
    fade = int(0.6 * RATE) if length else 0
    # Played faster for a higher pitch: output frame i reads source frame i * pitch.
    at0 = int(t * RATE)
    for i in range(min(int(frames / pitch), length_frames - at0)):
        j = int(i * pitch)
        gain = volume * (min(1.0, (frames - j) / fade) if fade else 1.0)
        k = 2 * (at0 + i)
        mix[k] += src[2 * j] * gain
        mix[k + 1] += src[2 * j + 1] * gain
with wave.open(audio, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(array.array('h', (max(-32768, min(32767, int(v))) for v in mix)).tobytes())
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# The thumbnail: a clean frame from the climb, two thirds in, with the mod's name large above it.
raw = sorted(n for n in os.listdir(frames_dir) if n.startswith('s_climb_'))
thumb = Image.open(os.path.join(frames_dir, raw[len(raw) * 2 // 3])).convert('RGBA').resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(thumb)
ttop = 300
for text, colour, size in (('SUMMITEER', (255, 255, 255), 128), ('WORTH CLIMBING', AMBER, 84)):
    big = font(FONTS, size)
    d.text(((W - d.textlength(text, font=big)) / 2, ttop), text, font=big, fill=colour, stroke_width=size // 12, stroke_fill=(0, 0, 0))
    ttop += size * 1.25
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
for scene in starts:
    n = sum(1 for s, _ in kept if s == scene)
    print(f'  {scene}: {n / FPS:.2f} s')
