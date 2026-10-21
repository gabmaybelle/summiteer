"""Builds the wide YouTube trailer (16:9, 1920x1080) from Trailer's frames.

python assemble_trailer.py <video_trailer dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes summiteer-trailer.mp4 (the game's own sounds), summiteer-trailer-silent.mp4 and two
thumbnails (1280x720): the player on the cliff with SUMMITEER, and the summit view with WORTH
CLIMBING. Music goes on after, with add_music.py.

The order and timings follow the plan (Gabby, 2026-10-21; SECTIONS below, about 74 s): the summit
view, then the title card; base camp (one quick shot of crafting the red parka, then the camera
turning round the dressed player); the wind on the ridge; the climb and the fall on the cliff; the
whiteout on the peak and the view clearing; the end card. Each section is fitted to its time by
starting a cut earlier or playing one faster (never slower than real time, at most 3x); the
report at the end shows any section still off the plan. Captions sit in the
lower third: bold white capitals with a black outline straight on the footage, one word in the
goggles' amber. Each cut comes from the moments Trailer logs (the clicks, the gusts, the landing,
the whiteout), so a new take needs no new numbers here. If a scene changes in Trailer, change it
here too.
"""
import array, csv, os, random, shutil, subprocess, sys, wave
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw, ImageFilter, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
AMBER = (242, 181, 74)
WHITE = (255, 255, 255)
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

W, H = 1920, 1080
NAME = 'summiteer-trailer'
SIZE, STROKE = 76, 7
CAPTION_Y = 770          # the caption's top: lower third, above the hearts and hotbar (GUI scale 3)
BADGE_BOX = (36, 32, 436, 112)
FADE_IN = 5

with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3] if len(r) > 3 else '') for r in csv.reader(f, delimiter='\t') if len(r) >= 3]


def at(scene, kind, nth=0):
    """The frame of the nth logged `kind` in `scene`, or None."""
    hits = [fr for s, fr, k, _ in actions if s == scene and k == kind]
    return hits[nth] if len(hits) > nth else None


def every(scene, kind):
    return [fr for s, fr, k, _ in actions if s == scene and k == kind]


def names(scene):
    return sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png') and not n.startswith('fail_'))


def count(scene):
    return len(names(scene))


def first(*values):
    return next((v for v in values if v is not None), None)


# ------------------------------------------------------------------ the cut
# Each segment: (scene, first frame, end frame, speed, caption key). Speed 2 keeps every other
# frame (a quick cut); the caption fades in when its key changes and stays across segments.

# Gabby's pick (2026-10-21): half plain, half climbers' jokes that still read for anyone.
CAPTIONS = {
    'gear': 'WOOL *ONLY*',
    'wind': 'STAY *LOW*',
    'climb': 'FREE *SOLO*',
    'fall': 'TRUST THE *AXE*',
    'top': 'FALSE *SUMMIT*?',
    'worth': 'MOUNTAINS *WORTH CLIMBING*',
}


def segments():
    """The cut, section by section, each fitted to the plan's timings (see SECTIONS)."""
    segs = []
    # 1. The summit view, the title card follows it.
    n = count('w_open')
    if n:
        segs.append(seg('open', 'w_open', max(0, n - 60), n, grow=True))
    # 2. Base camp: one quick shot of crafting the red parka, then the turn round the dressed player.
    if count('w_craft'):
        start = first(at('w_craft', 'table'), 0)
        end = first(at('w_craft', 'crafted'), count('w_craft'))
        segs.append(seg('gear', 'w_craft', max(0, start - 4), end + 14, speed=1.5, key='gear'))
    if count('w_turn'):
        segs.append(seg('gear', 'w_turn', 0, count('w_turn'), key='gear', fit=True))
    # 3. The ridge: the first gust from before its warning to after it lands, then the braced one.
    warn, hit = at('w_wind', 'gust_warning'), at('w_wind', 'gust')
    bwarn, braced = at('w_wind', 'braced_gust_warning'), at('w_wind', 'braced_gust')
    if warn is not None and hit is not None:
        segs.append(seg('wind', 'w_wind', max(0, warn - 12), hit + 40, key='wind', grow=True))
    if braced is not None:
        segs.append(seg('wind', 'w_wind', max(0, first(bwarn, braced - 40) - 10), braced + 40, key='wind', tail=True))
    if warn is None and braced is None and count('w_wind'):
        segs.append(seg('wind', 'w_wind', 0, count('w_wind'), key='wind', fit=True))
    # 4. The cliff: the climb and the hang (first person), then up to ten blocks, the hang and the fall.
    if count('w_climb'):
        segs.append(seg('cliff', 'w_climb', 0, count('w_climb'), key='climb', fit=True))
    if count('w_fall'):
        landed = at('w_fall', 'landed')
        segs.append(seg('cliff', 'w_fall', 0, (landed + 24) if landed is not None else count('w_fall'), key='fall', tail=True))
    # 5. The peak: the last steps, the whiteout and the goggles, the storm clearing (quicker), the view.
    n = count('w_summit')
    if n:
        top, clear, gone = at('w_summit', 'on_top'), at('w_summit', 'clear'), at('w_summit', 'fog_gone')
        start = max(0, top - 50) if top is not None else 0
        if clear is not None and gone is not None:
            segs.append(seg('summit', 'w_summit', start, clear, key='top', grow=True))
            segs.append(seg('summit', 'w_summit', clear, gone, speed=2, key='top', fit=True))
            segs.append(seg('summit', 'w_summit', gone, n, key='worth', tail=True))
        else:
            segs.append(seg('summit', 'w_summit', start, n, key='top', fit=True))
    for section in SECTIONS:
        fit_section(segs, section)
    return segs


# The plan's timings (Gabby, 2026-10-21): seconds per section, cards included. About 74 s in all.
SECTIONS = {'open': 6, 'gear': 8, 'wind': 15, 'cliff': 20, 'summit': 17, 'end': 8}
TITLE_FRAMES, END_FRAMES = 44, SECTIONS['end'] * FPS
MAX_SPEED = 3


def seg(section, scene, start, end, speed=1.0, key=None, grow=False, tail=False, fit=False):
    """One cut: `grow` lets its start move earlier to fill its section, `tail` its end later, and
    `fit` lets it speed up to fit."""
    return {'section': section, 'scene': scene, 'start': start, 'end': min(end, count(scene)), 'speed': speed,
            'key': key, 'grow': grow, 'tail': tail, 'fit': fit}


def frames_of(s):
    return max(0, int((s['end'] - s['start']) / s['speed'] + 0.999))


def fit_section(segs, section):
    """Moves a growable cut's start earlier (then a tail cut's end later) when the section is short,
    and speeds a fittable cut up (never above MAX_SPEED, never slower than real time) when it is long."""
    mine = [s for s in segs if s['section'] == section]
    if not mine:
        return
    target = SECTIONS[section] * FPS - (TITLE_FRAMES if section == 'open' else 0)
    short = target - sum(frames_of(s) for s in mine)
    for s in mine:
        if short > 0 and s['grow']:
            move = min(short * s['speed'], s['start'])
            s['start'] -= int(move)
            short -= int(move / s['speed'])
    for s in mine:
        if short > 0 and s['tail']:
            move = min(short * s['speed'], count(s['scene']) - s['end'])
            s['end'] += int(move)
            short -= int(move / s['speed'])
    for s in mine:
        if short < 0 and s['fit']:
            others = sum(frames_of(o) for o in mine if o is not s)
            room = max(1, target - others)
            s['speed'] = min(MAX_SPEED, max(s['speed'], (s['end'] - s['start']) / room))
            short = target - sum(frames_of(o) for o in mine)


# ------------------------------------------------------------------ drawing

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
TEXT_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


CAPTION = font(FONTS, SIZE)
ICON = Image.open(os.path.join(repo, 'common/src/main/resources/assets/summiteer/icon.png')).convert('RGBA')


def outlined(d, xy, line, fnt, stroke, anchor_centre=True):
    """One line with "*word*" in amber, centred on x (or starting at it), outlined in black."""
    parts = [(p, AMBER if i % 2 else WHITE) for i, p in enumerate(line.split('*')) if p]
    width = sum(d.textlength(p, font=fnt) for p, _ in parts)
    x = xy[0] - width / 2 if anchor_centre else xy[0]
    for text, colour in parts:
        d.text((x, xy[1]), text, font=fnt, fill=colour, stroke_width=stroke, stroke_fill=(0, 0, 0))
        x += d.textlength(text, font=fnt)
    return width


def caption(line):
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    outlined(ImageDraw.Draw(layer), (W / 2, CAPTION_Y), line, CAPTION, STROKE)
    return layer


def badge():
    """A small mod-list style badge, top left: the icon, the name, the loaders and version."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = BADGE_BOX
    d.rounded_rectangle(box, 16, fill=(20, 20, 24, 210))
    side = box[3] - box[1] - 20
    layer.alpha_composite(ICON.resize((side, side), Image.NEAREST), (box[0] + 10, box[1] + 10))
    x = box[0] + side + 24
    d.text((x, box[1] + 8), 'Summiteer', font=font(TEXT_FONTS, 32), fill=WHITE)
    d.text((x, box[1] + 48), 'Fabric · NeoForge · 26.3', font=font(TEXT_FONTS, 19), fill=(170, 170, 180))
    return layer


def faded(layer, a):
    if a >= 1:
        return layer
    layer = layer.copy()
    layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
    return layer


def backdrop(path):
    """A footage frame blurred and darkened, for a card to sit on."""
    img = Image.open(path).convert('RGB').resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14))
    return Image.blend(img, Image.new('RGB', (W, H), (8, 10, 16)), 0.55).convert('RGBA')


def title_card(path):
    """The icon, SUMMITEER and the tagline over the opening shot's last frame."""
    img = backdrop(path)
    d = ImageDraw.Draw(img)
    side = 220
    img.alpha_composite(ICON.resize((side, side), Image.NEAREST), ((W - side) // 2, 250))
    big = font(FONTS, 150)
    outlined(d, (W / 2, 500), 'SUMMITEER', big, 10)
    outlined(d, (W / 2, 690), 'MOUNTAINS *WORTH CLIMBING*', font(FONTS, 62), 6)
    return img


END_LINES = [
    ('Fabric and NeoForge  ·  Minecraft 26.3', WHITE),
    ('Needs Curios (NeoForge) or Trinkets Updated (Fabric)', (200, 200, 210)),
    ('Works with Tectonic and Terralith', (200, 200, 210)),
    ('Every feature can be switched off', (200, 200, 210)),
]


def end_card(path):
    """The facts: loaders and version, what it needs, what it works with, where to find it."""
    img = backdrop(path)
    d = ImageDraw.Draw(img)
    side, gap, name = 150, 28, font(FONTS, 104)
    left = (W - side - gap - d.textlength('SUMMITEER', font=name)) / 2
    img.alpha_composite(ICON.resize((side, side), Image.NEAREST), (int(left), 150))
    outlined(d, (left + side + gap, 180), 'SUMMITEER', name, 8, anchor_centre=False)
    y = 395
    text = font(TEXT_FONTS, 46)
    for line, colour in END_LINES:
        d.text(((W - d.textlength(line, font=text)) / 2, y), line, font=text, fill=colour)
        y += 70
    outlined(d, (W / 2, y + 50), 'SEARCH *SUMMITEER* ON CURSEFORGE', font(FONTS, 66), 6)
    return img


# ------------------------------------------------------------------ the frames

BADGE = badge()
work = os.path.join(out, 'work-trailer')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

# kept: one entry per output frame: (scene, source frame) or (card name, i).
kept = []


# Frames are listed first, then drawn in a thread pool: Pillow lets go of Python's lock while it
# decodes, resizes and encodes, so threads draw several at once (Cozy Core's showcase, 2026-10-21:
# one at a time took about 25 minutes for 1,800 frames). The in-between PNGs are saved with light
# compression: ffmpeg reads each once, then they are deleted.
jobs = []   # (output index, source path or card image, badge?, caption key or None, fade 0-1)
CAPTION_LAYERS = {key: caption(line) for key, line in CAPTIONS.items()}


def card(name, img, n):
    for i in range(n):
        jobs.append((len(kept), img, False, None, min(1.0, (i + 1) / 8)))
        kept.append((name, i))


segs = segments()
if not segs:
    sys.exit('no trailer frames in ' + frames_dir)
open_names = names('w_open')
shown_caption, since = None, 0
for s in segs:
    scene, start, end, speed, key = s['scene'], s['start'], s['end'], s['speed'], s['key']
    files = names(scene)
    picks, f = [], float(max(0, start))
    while f < min(end, len(files)):
        picks.append(int(f))
        f += speed
    if key != shown_caption:
        shown_caption, since = key, 0
    for n in picks:
        jobs.append((len(kept), os.path.join(frames_dir, files[n]), scene != 'w_open', key, min(1.0, (since + 1) / FADE_IN)))
        since += 1
        kept.append((scene, n))
    if scene == 'w_open':
        card('title', title_card(os.path.join(frames_dir, open_names[-1])), TITLE_FRAMES)
last = names(segs[-1]['scene'])[-1]
card('end', end_card(os.path.join(frames_dir, last)), END_FRAMES)
total = len(kept) / FPS
BLACK = Image.new('RGBA', (W, H), (0, 0, 0, 255))


def draw(job):
    index, source, badged, key, fade = job
    if isinstance(source, str):
        img = Image.open(source).convert('RGBA')
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        if badged:
            img.alpha_composite(BADGE)
        if key is not None:
            img.alpha_composite(faded(CAPTION_LAYERS[key], fade))
    else:
        # A card fades in from black.
        img = Image.alpha_composite(BLACK, faded(source, fade)) if fade < 1 else source
    img.convert('RGB').save(os.path.join(work, '%05d.png' % index), compress_level=1)


with ThreadPoolExecutor(os.cpu_count() or 4) as pool:
    for _ in pool.map(draw, jobs):
        pass

silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, '%05d.png'),
    '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-movflags', '+faststart', silent)

# ------------------------------------------------------------------ the sound
# The game's own sounds where they happen: clicks at the table, the campfire under the camp, the gust's whoosh at each warning, stone while climbing and
# sliding, snow underfoot on the peak and at the landing.
random.seed(7)
where = {}
for i, (scene, n) in enumerate(kept):
    where.setdefault((scene, n), i)


def cue_at(scene, frame, ahead=0):
    """The time of the first kept frame at or after `frame` (within `ahead` frames), or None."""
    for g in range(frame, frame + ahead + 1):
        i = where.get((scene, g))
        if i is not None:
            return i / FPS
    return None


cues = []


def cue(t, sound, pitch=1.0, volume=0.8, length=None):
    if t is not None:
        cues.append((t, sound, pitch, volume, length))


for scene in ('w_craft',):
    for kind in ('click', 'right_click'):
        for f in every(scene, kind):
            cue(cue_at(scene, f, 1), 'click', 1.0 + random.random() * 0.1, 0.35)
camp = [i for i, (s, _) in enumerate(kept) if s in ('w_craft', 'w_turn')]
for i in camp[::12]:
    cue(i / FPS, random.choice(['crackle1', 'crackle2', 'crackle3']), 0.95 + random.random() * 0.1, 0.35)
for kind in ('gust_warning', 'braced_gust_warning'):
    f = at('w_wind', kind)
    if f is not None:
        cue(cue_at('w_wind', f, 60), 'whoosh', 1.4, 0.7, 2.2)
hang, landed, top = at('w_climb', 'hang'), at('w_fall', 'landed'), at('w_summit', 'on_top')
for (s, n), i in where.items():
    if s == 'w_climb' and n % 8 == 0 and not (hang is not None and hang <= n < hang + 30):
        cue(i / FPS, random.choice(['stone1', 'stone2', 'stone3', 'stone4']), 0.9 + random.random() * 0.2, 0.5)
    if s == 'w_fall' and n % 6 == 0 and (landed is None or n < landed):
        cue(i / FPS, random.choice(['stone1', 'stone2', 'stone3', 'stone4']), 0.8 + random.random() * 0.1, 0.4)
    if s == 'w_summit' and top is not None and n < top and n % 7 == 0:
        cue(i / FPS, random.choice(['snow1', 'snow3', 'snow4']), 0.9 + random.random() * 0.2, 0.6)
if landed is not None:
    cue(cue_at('w_fall', landed, 2), 'snow2', 1.0, 0.9)
f = at('w_summit', 'goggles_on')
if f is not None:
    cue(cue_at('w_summit', f, 2), 'leather1', 1.0, 0.8)
cues.sort()

audio = os.path.join(work, 'sound.wav')
# The mix is laid down here in Python, not in one big ffmpeg graph: ffmpeg 9 stalled for good
# partway through an 84-input amix (2026-10-21). ffmpeg only decodes each sound once.
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
    # Played faster for a higher pitch (as asetrate did): output frame i reads source frame i * pitch.
    out_frames = int(frames / pitch)
    at0 = int(t * RATE)
    for i in range(min(out_frames, length_frames - at0)):
        j = int(i * pitch)
        gain = volume * (min(1.0, (frames - j) / fade) if fade else 1.0)
        k = 2 * (at0 + i)
        mix[k] += src[2 * j] * gain
        mix[k + 1] += src[2 * j + 1] * gain
pcm16 = array.array('h', (max(-32768, min(32767, int(v))) for v in mix))
with wave.open(audio, 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(pcm16.tobytes())
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# ------------------------------------------------------------------ thumbnails (1280x720)


def thumbnail(source, words, name):
    img = Image.open(source).convert('RGBA').resize((1280, 720), Image.LANCZOS)
    d = ImageDraw.Draw(img)
    y = 70
    for text, size in words:
        outlined(d, (640, y), text, font(FONTS, size), max(4, size // 12))
        y += int(size * 1.2)
    img.convert('RGB').save(os.path.join(out, name))


fall = names('w_fall')
if fall:
    # The player on the wall with the range behind, before letting go.
    thumbnail(os.path.join(frames_dir, fall[min(3, len(fall) - 1)]), [('SUMMITEER', 120)], NAME + '-thumbnail-cliff.png')
if open_names:
    thumbnail(os.path.join(frames_dir, open_names[len(open_names) * 2 // 3]), [('*WORTH CLIMBING*', 104)], NAME + '-thumbnail-summit.png')

print('total seconds', round(total, 2), '(plan: about %d)' % sum(SECTIONS.values()))
# Each section against the plan's timings, so a take that runs short or long shows here.
section_of = {s['scene']: s['section'] for s in segs}
section_of.update({'title': 'open', 'end': 'end'})
for section, target in SECTIONS.items():
    got = sum(1 for scene, _ in kept if section_of.get(scene) == section) / FPS
    flag = '' if abs(got - target) <= 1.5 else '   <- off the plan by %+.1f s' % (got - target)
    print(f'  {section}: {got:.2f} s (plan {target}){flag}')
