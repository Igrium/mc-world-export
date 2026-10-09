"""Run the opt-in synthetic harness in a NEW runtime using existing licensed game assets.
No normal instance, user profile, custom mod, resource pack or server is modified.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
for name in ('runtime', 'java', 'libraries', 'assets', 'exporter', 'replaymod', 'fabric-api', 'harness', 'blender', 'addon'):
    parser.add_argument('--' + name, type=Path, required=True)
parser.add_argument('--asset-index', required=True)
args = parser.parse_args()
repository = Path(__file__).resolve().parents[1]
runtime = args.runtime.resolve()
if runtime.exists() and any(runtime.iterdir()):
    raise ValueError('Use a new, empty test runtime; existing instances are never overwritten')
for path in (args.java, args.exporter, args.replaymod, args.fabric_api, args.harness, args.blender, args.addon):
    if not path.is_file(): raise ValueError('Required input missing: ' + str(path))
index = args.assets.resolve() / 'indexes' / (args.asset_index + '.json')
if not index.is_file(): raise ValueError('Minecraft asset index missing')
libraries = sorted(args.libraries.resolve().glob('*.jar'))
if not libraries: raise ValueError('Supply the existing Minecraft/Fabric/native library directory')
mods = runtime / 'mods'
mods.mkdir(parents=True)
for path in (args.exporter, args.replaymod, args.fabric_api, args.harness):
    shutil.copy2(path, mods / path.name)
(runtime / 'options.txt').write_text('resourcePacks:["vanilla"]\nrenderDistance:4\nsimulationDistance:6\n')
package = runtime / 'packaged' / 'worldexport_blender'
package.mkdir(parents=True)
with zipfile.ZipFile(args.addon) as archive:
    for item in archive.infolist():
        path = Path(item.filename)
        if path.is_absolute() or '..' in path.parts: raise ValueError('Unsafe addon path')
    archive.extractall(package)

def run(command, name, env, timeout):
    with (runtime / (name + '.log')).open('w') as log:
        process = subprocess.Popen(command, cwd=runtime, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
            raise RuntimeError(name + ' timed out; only its test process was stopped')
    if code: raise RuntimeError(name + ' failed; inspect its log')
    print(name + ' passed', flush=True)

env = dict(os.environ, ALSOFT_DRIVERS='null', PYTHONDONTWRITEBYTECODE='1')
if platform.system() == 'Linux': env.update(SDL_VIDEODRIVER='x11')
run([str(args.java.resolve()), '-Xmx4G', '--enable-native-access=ALL-UNNAMED',
     '--sun-misc-unsafe-memory-access=allow', '-cp', os.pathsep.join(map(str,libraries)),
     'net.fabricmc.loader.impl.launch.knot.KnotClient', '--username', 'CompatTest', '--version', '26.3',
     '--gameDir', str(runtime), '--assetsDir', str(args.assets.resolve()), '--assetIndex', args.asset_index,
     '--uuid', '00000000000000000000000000000000', '--accessToken', '0', '--userType', 'legacy',
     '--width', '960', '--height', '540'], 'minecraft', env, 300)
log = (runtime / 'minecraft.log').read_text(errors='replace')
if 'ALL_TESTS_OK' not in log or '[COMPAT] FAILED' in log: raise RuntimeError('Harness checks failed')
scene = runtime / 'compat-scene.replay'
with zipfile.ZipFile(scene) as archive:
    environment = json.loads(archive.read('environment.json'))
    events = json.loads(archive.read('events.json'))
    sounds = json.loads(archive.read('sounds.json'))
    appearance = json.loads(archive.read('appearance.json'))
    kinds = [cue['kind'] for cue in events['cues']]
    for kind in ('example:quest_completed', 'example:level_changed', 'example:story_beat'):
        assert kinds.count(kind) == 1, 'Network duplicate was not deduplicated'
    assert kinds.count('example:progress_snapshot') == 2, 'Opt-in adapter did not preserve both snapshots'
    assert 'example:bad' not in kinds, 'Invalid or unknown-version payload was captured'
    assert len(kinds) == 5, 'Unexpected semantic capture'
    assert any(key['raining'] for key in environment['keys']) and any(not key['raining'] for key in environment['keys'])
    assert max(key['defaultClockTime'] for key in environment['keys']) - min(key['defaultClockTime'] for key in environment['keys']) > 10000
    assert sounds['cues']
    legacy = runtime / 'legacy.replay'
    with zipfile.ZipFile(legacy, 'w', zipfile.ZIP_DEFLATED) as output:
        for entry in archive.infolist():
            if entry.filename not in ('appearance.json', 'sounds.json', 'environment.json', 'events.json'):
                output.writestr(entry, archive.read(entry.filename))

env.update(WORLDEXPORT_ADDON_ROOT=str(package.parent), WORLDEXPORT_STOCK_INDEX=str(index))
tests = [('blender_timeline.py', [str(scene), str(runtime / 'timeline-blender')]),
         ('blender_sound_cues.py', [str(scene), str(legacy), str(runtime / 'sound-blender')]),
         ('blender_appearance_smoke.py', [str(scene), str(runtime / 'appearance-blender'), str(legacy)]),
         ('blender_pbr_channels.py', [])]
for script, extra in tests:
    command = [str(args.blender.resolve()), '--background', '--factory-startup', '--threads', '4',
               '--python-exit-code', '1', '--python', str(repository / 'tests' / script)]
    if extra: command += ['--'] + extra
    run(command, script[:-3], env, 300)
result = {'version': '1.5.0-public.1+mc26.3', 'platform': platform.system(),
          'minecraftHarnessPassed': True, 'privateModsRequired': False, 'syntheticEventFixture': True,
          'environmentSamples': len(environment['keys']), 'eventCues': len(events['cues']),
          'soundCues': len(sounds['cues']), 'appearance': appearance['captureReport'],
          'eventAdapterReplayVerified': True, 'networkDedupVerified': True,
          'malformedAndUnknownVersionEventsIgnored': True, 'blenderTestsPassed': [name for name, extra in tests],
          'modHashes': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(mods.glob('*.jar'))},
          'audioBackend': 'null; audible playback not tested', 'fullModSuiteTested': False, 'longRecordingTested': False}
(runtime / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print('PUBLIC_CAPTURE_SMOKE_OK ' + json.dumps(result), flush=True)
