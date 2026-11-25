#!/usr/bin/env python3
"""Run the production-mapped test mod in a fresh disposable Forge server (Python 3.11+)."""
import argparse
import ctypes
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
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
parser.add_argument('--smoke-jar', type=Path, help='Pin the test instrumentation jar across a comparison')
parser.add_argument('--no-build', action='store_true')
parser.add_argument('--datapack', type=Path, action='append', default=[], help='Installed datapack zip/jar to load in the fixture')
parser.add_argument('--java-arg', action='append', default=[], help='Additional JVM argument (use --java-arg=VALUE)')
parser.add_argument('--seed', type=int, default=104729, help='World seed for the disposable server')
parser.add_argument('--xms', default='1G', help='Initial JVM heap')
parser.add_argument('--xmx', default='4G', help='Maximum JVM heap')
parser.add_argument('--runtime-parent', type=Path, help='Directory for disposable fixture servers')
parser.add_argument('--no-mixin-export', action='store_true', help='Disable class-file export during throughput measurements')
parser.add_argument('--server-template', type=Path, help='Copy a complete exported server pack instead of a minimal mod set')
parser.add_argument('--world-template', type=Path, help='Copy a saved world for a continuation run')
parser.add_argument('--view-distance', type=int, default=2)
parser.add_argument('--simulation-distance', type=int, default=2)
parser.add_argument('--threadtweak-workers', type=int, help='Override main workers in this disposable fixture only')
parser.add_argument('--identity-candidate', action='store_true', help='Enable the experimental identity shortcut in the test mod only')
args = parser.parse_args()
properties = dict(line.split('=', 1) for line in (ROOT/'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = properties['mod_version']
if not args.no_build:
    subprocess.run([str(ROOT/'gradlew'), 'reobfJarJar', 'reobfSmokeTestJar'], cwd=ROOT, check=True)

if args.runtime_parent:
    args.runtime_parent.mkdir(parents=True, exist_ok=True)
runtime = Path(tempfile.mkdtemp(prefix=f'auram-{args.mode}-', dir=args.runtime_parent))

def copy_private(source, target):
    # APFS clonefile gives each fixture independent writable files without
    # copying a gigabyte of mod jars for every JVM comparison.
    if sys.platform == 'darwin' and ctypes.CDLL(None, use_errno=True).clonefile(
            os.fsencode(source), os.fsencode(target), 0) == 0:
        return str(target)
    return shutil.copy2(source, target)

if args.server_template:
    for entry in args.server_template.iterdir():
        if entry.name in {'libraries', 'world', 'logs', 'crash-reports'}:
            continue
        target=runtime/entry.name
        if entry.is_dir():
            shutil.copytree(entry, target, copy_function=copy_private)
        else:
            copy_private(entry, target)
if args.world_template:
    shutil.copytree(args.world_template, runtime/'world', copy_function=copy_private)
(runtime/'mods').mkdir(exist_ok=True)
# Remove the distributed core by mod ID; renamed/versioned jars must not leave
# a second auram mod in a full-server fixture.
if args.server_template:
    for jar in (runtime/'mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            try:
                mods=tomllib.loads(archive.read('META-INF/mods.toml').decode()).get('mods', [])
            except KeyError:
                mods=[]
            if any(mod.get('modId')=='auram' for mod in mods):
                jar.unlink()
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
        if not target.exists():
            target.write_bytes(data)
        copied.add(label)
    fixture[mod_id] = {'source': label, 'sha256': hashlib.sha256(data).hexdigest()}
    wanted.update(dep['modId'] for dep in meta.get('dependencies', {}).get(mod_id, [])
                  if dep.get('mandatory') and dep.get('side') != 'CLIENT')

artifact = args.baseline_jar or ROOT/f'build/libs/auram-{version}-all.jar'
shutil.copy2(artifact, runtime/'mods/Auram.jar')
smoke_artifact = args.smoke_jar or ROOT/f'build/libs/auram-{version}-smoke-test.jar'
shutil.copy2(smoke_artifact, runtime/'mods/auram-smoke.jar')
config = runtime/'config'
config.mkdir(exist_ok=True)
fixture_overrides = {}
if args.threadtweak_workers is not None:
    scheduling = config/'auram/thread-scheduling.json'
    settings = json.loads(scheduling.read_text()) if scheduling.exists() else {}
    if not settings.get('enabled'):
        scheduling = config/'threadtweak.json'
        settings = json.loads(scheduling.read_text())
    settings['threadCount']['main'] = args.threadtweak_workers
    scheduling.write_text(json.dumps(settings, indent=2)+'\n')
    fixture_overrides['threadtweak_main_workers'] = args.threadtweak_workers
(config/'fml.toml').write_text('versionCheck = false\n')
source_config = Path(properties['modpack_deploy']).parent/'config/aether_redux_common.toml'
if 'aether_redux' in wanted:
    shutil.copy2(source_config, config/source_config.name)
(runtime/'eula.txt').write_text('eula=true\n')
ground = '{"block":"minecraft:stone","height":71},' if args.mode == 'ftbrtp' else ''
(runtime/'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\n'
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
    f'{ground}'
    '{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
    f'level-seed={args.seed}\nmax-tick-time=-1\nview-distance={args.view_distance}\n'
    f'simulation-distance={args.simulation_distance}\nspawn-protection=0\n')
if args.mode in {'ring-search', 'worldgen-bench', 'tuning', 'structureprobes', 'well', 'terrain', 'density', 'heightcache', 'mappingperf', 'wrought', 'sparse', 'terrainbatch', 'boundedslice', 'twigs', 'lithostarts', 'bronzeparity', 'scatterparity', 'regionparity', 'aquiferparity', 'surfacejitter', 'surfaceterrain'}:
    settings = (runtime/'server.properties').read_text().splitlines()
    (runtime/'server.properties').write_text('\n'.join(
        'level-type=minecraft:normal' if s.startswith('level-type=') else
        'generator-settings={}' if s.startswith('generator-settings=') else s for s in settings)+'\n')
for datapack in args.datapack:
    destination=runtime/'world/datapacks'
    destination.mkdir(parents=True,exist_ok=True)
    shutil.copy2(datapack,destination/(datapack.stem+'.zip'))
command = [args.java, *([] if args.xms=='default' else [f'-Xms{args.xms}']), f'-Xmx{args.xmx}', *args.java_arg, *([] if args.no_mixin_export else ['-Dmixin.debug.export=true']), f'-Dauram.smoke={args.mode}',
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
if args.mode == 'worldgen-bench':
    marker = 'worldgen-chunks.csv'
passed = all(code == 0 for code in codes) and (runtime/marker).exists()
summary = {'mode': args.mode, 'runtime': str(runtime), 'passed': passed, 'exit_codes': codes,
           'elapsed_seconds': time.monotonic()-start, 'artifact_sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
           'smoke_sha256': hashlib.sha256((runtime/'mods/auram-smoke.jar').read_bytes()).hexdigest(),
           'fixture': fixture, 'jvm_command': command,
           'server_template': str(args.server_template) if args.server_template else None,
           'fixture_overrides': fixture_overrides,
           'mod_jar_count': len(list((runtime/'mods').glob('*.jar'))),
           'datapacks': [{'source': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                        for path in args.datapack]}
(runtime/'result.json').write_text(json.dumps(summary, indent=2)+'\n')
print(json.dumps(summary, indent=2), flush=True)
raise SystemExit(0 if passed else 1)
