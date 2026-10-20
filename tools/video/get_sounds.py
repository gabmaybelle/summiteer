"""Copies the game sounds the Short uses out of Loom's asset cache (filled by any game-test run).

python get_sounds.py <out dir>
The sounds are Mojang's, so they're read from the cache each time rather than kept in the repo.
"""
import glob, json, os, shutil, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
assets = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
# The gust's whoosh is the elytra's (what the mod plays); the climb is stone steps; the landing snow.
SOUNDS = {'whoosh': 'item/elytra/elytra_loop',
          'stone1': 'step/stone1', 'stone2': 'step/stone2', 'stone3': 'step/stone3', 'stone4': 'step/stone4',
          'snow1': 'step/snow1', 'snow2': 'step/snow2', 'snow3': 'step/snow3', 'snow4': 'step/snow4'}
objects = {}
for index in sorted(glob.glob(os.path.join(assets, 'indexes', '*.json'))):
    objects.update(json.load(open(index))['objects'])
for name, file in SOUNDS.items():
    h = objects[f'minecraft/sounds/{file}.ogg']['hash']
    source = os.path.join(assets, 'objects', h[:2], h)
    if not os.path.exists(source):
        sys.exit(f'{file}.ogg is not in the asset cache; run a game test with resources.download.minecraft.net reachable')
    shutil.copy(source, os.path.join(out, name + '.ogg'))
    print(name)
