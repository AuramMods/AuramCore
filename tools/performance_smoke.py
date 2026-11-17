#!/usr/bin/env python3
"""Run the production-mapped test mod in a fresh disposable Forge server (Python 3.11+)."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java', required=True, help='Java 17 executable')
parser.add_argument('--libraries', required=True, type=Path, help='Installed Forge server libraries directory')
parser.add_argument('--mode', default='movement')
parser.add_argument('--with-mod', action='append', default=[], dest='extra_mods', help='Extra mod id and its dependencies')
parser.add_argument('--baseline-jar', type=Path, help='Use another Auram jar as a regression control')
parser.add_argument('--no-build', action='store_true')
parser.add_argument('--identity-candidate', action='store_true', help='Enable the experimental identity shortcut in the test mod only')
args = parser.parse_args()
properties = dict(line.split('=', 1) for line in (ROOT/'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = properties['mod_version']
if not args.no_build:
    subprocess.run([str(ROOT/'gradlew'), 'reobfJarJar', 'reobfSmokeTestJar'], cwd=ROOT, check=True)

runtime = Path(tempfile.mkdtemp(prefix=f'auram-{args.mode}-'))
(runtime/'mods').mkdir()
(runtime/'libraries').symlink_to(args.libraries.resolve(), target_is_directory=True)
mod_index = {}

def index_jar(source, label, nested=False):
    with zipfile.ZipFile(source if isinstance(source, Path) else io.BytesIO(source)) as archive:
        try:
            meta = tomllib.loads(archive.read('META-INF/mods.toml').decode())
        except (KeyError, tomllib.TOMLDecodeError):
            meta = {}
        for mod in meta.get('mods', []):
            entry = (source, label, meta)
            if mod['modId'] not in mod_index or not nested:
                mod_index[mod['modId']] = entry
        try:
            jars = json.loads(archive.read('META-INF/jarjar/metadata.json'))['jars']
        except KeyError:
            jars = []
        for jar in jars:
            index_jar(archive.read(jar['path']), label+'!'+jar['path'], True)

# Resolve actual installed dependencies, including libraries bundled by other mods.
for jar in Path(properties['modpack_deploy']).glob('*.jar'):
    index_jar(jar, str(jar))
wanted = {'projecte', *args.extra_mods}
done = set()
copied = set()
fixture = {}
while wanted - done:
    mod_id = sorted(wanted - done)[0]
    done.add(mod_id)
    if mod_id in {'forge', 'minecraft', 'auram'}:
        continue
    if mod_id not in mod_index:
        print(f'No indexed jar for {mod_id}; letting Forge report any missing dependency.', flush=True)
        continue
    source, label, meta = mod_index[mod_id]
    data = source.read_bytes() if isinstance(source, Path) else source
    if label not in copied:
        target = runtime/'mods'/Path(label.split('!')[-1]).name
        target.write_bytes(data)
        copied.add(label)
    fixture[mod_id] = {'source': label, 'sha256': hashlib.sha256(data).hexdigest()}
    wanted.update(dep['modId'] for dep in meta.get('dependencies', {}).get(mod_id, [])
                  if dep.get('mandatory') and dep.get('side') != 'CLIENT')

artifact = args.baseline_jar or ROOT/f'build/libs/auram-{version}-all.jar'
shutil.copy2(artifact, runtime/'mods/Auram.jar')
shutil.copy2(ROOT/f'build/libs/auram-{version}-smoke-test.jar', runtime/'mods/auram-smoke.jar')
config = runtime/'config'
config.mkdir()
(config/'fml.toml').write_text('versionCheck = false\n')
source_config = Path(properties['modpack_deploy']).parent/'config/aether_redux_common.toml'
if 'aether_redux' in wanted:
    shutil.copy2(source_config, config/source_config.name)
(runtime/'eula.txt').write_text('eula=true\n')
(runtime/'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
    '{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
    'level-seed=104729\nmax-tick-time=-1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
if args.mode in {'well', 'terrain', 'density', 'heightcache', 'mappingperf', 'wrought', 'sparse', 'terrainbatch'}:
    settings = (runtime/'server.properties').read_text().splitlines()
    (runtime/'server.properties').write_text('\n'.join(
        'level-type=minecraft:normal' if s.startswith('level-type=') else
        'generator-settings={}' if s.startswith('generator-settings=') else s for s in settings)+'\n')
command = [args.java, '-Xms1G', '-Xmx4G', '-Dmixin.debug.export=true', f'-Dauram.smoke={args.mode}',
           f'-Dauram.smoke.identityCandidate={str(args.identity_candidate).lower()}',
           '@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt', 'nogui']
print(f'Runtime: {runtime}', flush=True)
start = time.monotonic()
codes = []
for phase in range(2 if args.mode == 'chunks' else 1):
    with (runtime/f'console-{phase+1}.log').open('w') as log:
        result = subprocess.run(command, cwd=runtime, stdout=log, stderr=subprocess.STDOUT, timeout=1800)
    codes.append(result.returncode)
marker = 'auram-smoke-pass.txt' if args.mode == 'chunks' else f'auram-{args.mode}-pass.txt'
passed = all(code == 0 for code in codes) and (runtime/marker).exists()
summary = {'mode': args.mode, 'runtime': str(runtime), 'passed': passed, 'exit_codes': codes,
           'elapsed_seconds': time.monotonic()-start, 'artifact_sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
           'fixture': fixture}
(runtime/'result.json').write_text(json.dumps(summary, indent=2)+'\n')
print(json.dumps(summary, indent=2), flush=True)
raise SystemExit(0 if passed else 1)
