#!/usr/bin/env python3
"""Run a hidden full-pack Prism client in a disposable instance; never touch live saves/settings."""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, default=Path('/Users/cyberpwn/Library/Application Support/PrismLauncher/instances/Auram'))
parser.add_argument('--prism', default='/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher')
parser.add_argument('--prism-root', type=Path, help='Use an isolated, already configured Prism data directory')
parser.add_argument('--runs', type=int, default=3)
parser.add_argument('--max-memory', type=int, help='Maximum client heap in MiB')
parser.add_argument('--mode', choices=('startup', 'world-startup', 'forge-remote'), default='startup')
parser.add_argument('--baseline-jar', type=Path)
parser.add_argument('--seed', type=int, default=104729)
parser.add_argument('--connect', help='Join a disposable server by host:port')
parser.add_argument('--lan-port', type=int, help='Host a disposable integrated world on the specified loopback port')
parser.add_argument('--minimal-network', action='store_true', help='Keep only AuramCore and the smoke mod dependencies')
parser.add_argument('--eager-rings', action='store_true')
parser.add_argument('--verify-rings', action='store_true')
parser.add_argument('--disable-tooltip-search', action='store_true')
parser.add_argument('--disable-distant-generation', action='store_true')
parser.add_argument('--jfr', action='store_true', help='Record the client with Java Flight Recorder')
parser.add_argument('--tooltip-layout', action='store_true', help='Render and check tooltips during startup smoke')
parser.add_argument('--smooth-scroll', action='store_true', help='Run scroll interactions in the disposable world')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--transitions', action='store_true', help='Check title, overlay and toast transitions in the disposable world')
parser.add_argument('--anvil-sounds', action='store_true', help='Check anvil sound events in the disposable world')
args = parser.parse_args()
version = next(line.split('=', 1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
artifact = args.baseline_jar or ROOT/f'build/libs/auram-{version}-all.jar'
smoke = ROOT/f'build/libs/auram-{version}-smoke-test.jar'
instance_id = 'Auram-Startup-QA-' + uuid.uuid4().hex[:8]
world_test = args.mode == 'world-startup'
marker_prefix = {'startup':'auram-startup-client', 'world-startup':'auram-world-startup',
                 'forge-remote':'auram-forge-remote'}[args.mode]
pass_marker = marker_prefix + '-pass.txt'
fail_marker = marker_prefix + '-failed.txt'
instances = args.prism_root/'instances' if args.prism_root else args.pack.parent
instance = instances/instance_id
game = instance/'minecraft'
game.mkdir(parents=True)
args.output.mkdir(parents=True, exist_ok=True)

def copy_private(source, target):
    if sys.platform == 'darwin' and ctypes.CDLL(None).clonefile(os.fsencode(source), os.fsencode(target), 0) == 0:
        return str(target)
    return shutil.copy2(source, target)

for name in ['mods', 'config', 'defaultconfigs', 'kubejs', 'scripts', 'resourcepacks', 'globalresources',
             'moonlight-global-datapacks', 'patchouli_books', 'modernfix', 'quests_structures', '.connector']:
    source = args.pack/'minecraft'/name
    if source.is_dir(): shutil.copytree(source, game/name, copy_function=copy_private)
shutil.copy2(args.pack/'mmc-pack.json', instance/'mmc-pack.json')
settings = (args.pack/'instance.cfg').read_text()
jvm_flags = ['-XX:+UseG1GC', f'-Dauram.smoke={args.mode}', '-Dauram.smoke.hiddenWindow=true',
             f'-Dauram.smoke.seed={args.seed}', f'-Dauram.worldgen.eagerStructureRings={str(args.eager_rings).lower()}']
if world_test:
    # A hidden Prism console can stop consuming the Java stdout pipe during
    # synchronous integrated-server startup. Keep probe output in its fixture.
    (game/'auram-smoke-log4j2.xml').write_text("""<Configuration>
  <Appenders>
    <File name="Latest" fileName="logs/latest.log">
      <PatternLayout pattern="[%d{HH:mm:ss.SSS}] [%t/%p] [%c]: %m%n"/>
    </File>
  </Appenders>
  <Loggers>
    <Root level="info"><AppenderRef ref="Latest"/></Root>
  </Loggers>
</Configuration>
""")
    jvm_flags.append('-Dlog4j2.configurationFile=auram-smoke-log4j2.xml')
if args.connect:
    jvm_flags.append(f'-Dauram.smoke.connect={args.connect}')
if args.lan_port:
    jvm_flags.append(f'-Dauram.smoke.lanPort={args.lan_port}')
if args.verify_rings:
    jvm_flags.append('-Dauram.smoke.verifyRings=true')
if args.tooltip_layout:
    jvm_flags.append('-Dauram.smoke.tooltipLayout=true')
if args.smooth_scroll:
    jvm_flags.append('-Dauram.smoke.smoothScroll=true')
if args.transitions:
    jvm_flags.append('-Dauram.smoke.transitions=true')
if args.anvil_sounds:
    jvm_flags.append('-Dauram.smoke.anvilSounds=true')
if args.jfr:
    jvm_flags.append('-XX:StartFlightRecording=filename=auram-world-startup.jfr,settings=profile,dumponexit=true')
jvm_args = '"' + ' '.join(jvm_flags) + '"'
max_memory = args.max_memory or (4096 if args.minimal_network else 16000)
overrides = {'name': instance_id, 'uuid': str(uuid.uuid4()), 'JvmArgs': jvm_args,
             'OverrideJavaArgs': 'true', 'OverrideMemory': 'true', 'MinMemAlloc': '2048', 'MaxMemAlloc': str(max_memory),
             'OverrideConsole': 'true', 'ShowConsole': 'false', 'ShowConsoleOnError': 'false', 'AutoCloseConsole': 'true',
             'LaunchMaximized': 'false', 'MinecraftWinWidth': '854', 'MinecraftWinHeight': '480', 'JoinServerOnLaunch': 'false'}
lines=[]
for line in settings.splitlines():
    key=line.split('=', 1)[0]
    if key in overrides: line=key+'='+overrides.pop(key)
    lines.append(line)
position = lines.index('[UI]') if '[UI]' in lines else len(lines)
lines[position:position]=[key+'='+value for key,value in overrides.items()]
(instance/'instance.cfg').write_text('\n'.join(lines)+'\n')
for path in (game/'mods').glob('AuramCore-*.jar'): path.unlink()
if (game/'mods/Auram.jar').exists(): (game/'mods/Auram.jar').unlink()
if args.minimal_network:
    for mod in (game/'mods').glob('*.jar'):
        if not mod.name.startswith(('ProjectE-', 'Patchouli-')):
            mod.unlink()
shutil.copy2(artifact, game/'mods'/f'AuramCore-{version}.jar')
shutil.copy2(smoke, game/'mods/auram-startup-smoke.jar')
fml=game/'config/fml.toml'
text=fml.read_text()
text='\n'.join('earlyWindowControl = false' if line.startswith('earlyWindowControl =') else line for line in text.splitlines())+'\n'
fml.write_text(text)
if args.disable_tooltip_search:
    jei = game/'config/jei/jei-client.ini'
    jei.write_text(jei.read_text().replace('TooltipSearchMode = ENABLED', 'TooltipSearchMode = DISABLED'))
if args.disable_distant_generation:
    dh = game/'config/DistantHorizons.toml'
    dh.write_text(dh.read_text().replace('generatorPlan = "SURFACE_THEN_CHUNKS"', 'generatorPlan = "DISABLED"'))

def java_pids():
    processes = subprocess.check_output(['ps', '-axo', 'pid=,ucomm=,args='], text=True)
    result = []
    for line in processes.splitlines():
        fields = line.split(None, 2)
        if len(fields) == 3:
            pid, executable, arguments = fields
            if executable == 'java' and instance_id in arguments and 'org.prismlauncher.EntryPoint' in arguments:
                result.append(int(pid))
    return result


def signal_clients(pids, sig):
    for pid in pids:
        try:
            os.kill(pid, sig)
        except ProcessLookupError:
            pass


def finish_launch(launcher):
    # Stop this launcher's process first, so an interrupted launch cannot start a client during cleanup.
    if launcher.poll() is None:
        launcher.terminate()
    signal_clients(java_pids(), signal.SIGTERM)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline and (launcher.poll() is None or java_pids()):
        time.sleep(0.25)
    if launcher.poll() is None:
        launcher.kill()
    launcher.wait()
    signal_clients(java_pids(), signal.SIGKILL)
    deadline = time.monotonic() + 5
    while java_pids():
        if time.monotonic() >= deadline:
            raise RuntimeError(f'QA client {instance_id} is still running after shutdown')
        time.sleep(0.25)


def stop_runner(sig, frame):
    raise SystemExit(128 + sig)


signal.signal(signal.SIGTERM, stop_runner)
signal.signal(signal.SIGHUP, stop_runner)

results=[]
summary={'instance':str(instance),'artifact_sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),
         'mod_jars':len(list((game/'mods').glob('*.jar'))),'runs':results}
print('Fixture: '+str(instance),flush=True)
for run in range(1,args.runs+1):
    if world_test:
        shutil.rmtree(game/'saves/auram-world-startup-smoke', ignore_errors=True)
    for marker in [pass_marker, fail_marker, 'startup-client-results.json']:
        (game/marker).unlink(missing_ok=True)
    shutil.copy2(args.pack/'minecraft/options.txt',game/'options.txt')
    options = game/'options.txt'
    lines = [line for line in options.read_text().splitlines() if not line.startswith('soundCategory_master:')]
    if world_test:
        lines = ['pauseOnLostFocus:false' if line == 'pauseOnLostFocus:true' else line for line in lines]
    options.write_text('\n'.join([*lines, 'soundCategory_master:0.0'])+'\n')
    log=game/'logs/latest.log'
    if log.exists(): log.unlink()
    start=time.monotonic()
    command = [args.prism]
    if args.prism_root:
        command.extend(['--dir', str(args.prism_root)])
    command.extend(['--launch', instance_id, '--offline', 'AuramQA' if not args.connect else 'AuramQARemote'])
    with (args.output/f'launcher-{run}.log').open('w') as console:
        launcher = subprocess.Popen(command, stdout=console, stderr=subprocess.STDOUT)
    seen=False; reason=None
    try:
        while time.monotonic()-start < (900 if world_test else 360):
            alive = java_pids()
            seen = seen or bool(alive)
            if launcher.poll() not in (None, 0) and not alive:
                reason = f'launcher exited with status {launcher.returncode}'
                break
            if log.exists():
                with log.open('rb') as current:
                    current.seek(max(0, log.stat().st_size - 262144))
                    recent = current.read()
                    if b'Failed to complete lifecycle event CONSTRUCT' in recent:
                        reason = 'mod construction failed'
                        break
                    if world_test and b"Failed to load datapacks, can't proceed with server load" in recent:
                        reason = 'world datapack load failed'
                        break
            if args.lan_port and (game/'auram-forge-remote-server-pass.txt').exists():
                (game/pass_marker).write_text('LAN client joined.\n')
                break
            terminal = (game/pass_marker).exists() or (game/fail_marker).exists()
            if not alive and (seen or terminal):
                break
            time.sleep(1)
        else:
            reason = 'client timeout'
    finally:
        finish_launch(launcher)
    passed=(game/pass_marker).exists() and reason is None
    record={'run':run,'passed':passed,'elapsed_seconds':time.monotonic()-start,'reason':reason}
    if (game/'startup-client-results.json').exists():record['checks']=json.loads((game/'startup-client-results.json').read_text())
    if (game/fail_marker).exists():record['failure']=(game/fail_marker).read_text()
    if passed and world_test and not args.lan_port:
        record['world_timings'] = dict(line.split('=', 1) for line in (game/pass_marker).read_text().splitlines())
    if log.exists():shutil.copy2(log,args.output/f'client-{run}.log')
    if world_test and (game/'logs/debug.log').exists():
        shutil.copy2(game/'logs/debug.log', args.output/f'client-{run}-debug.log')
    for artifact_name in ('auram-ring-search-pass.txt', 'auram-world-startup.jfr', 'auram-smooth-scroll-smoke.json',
                          'auram-transition-smoke.txt', 'auram-anvil-sounds-smoke.txt'):
        path = game/artifact_name
        if path.exists():
            shutil.copy2(path, args.output/f'{run}-{artifact_name}')
    if (game/'startup-menu.png').exists():shutil.copy2(game/'startup-menu.png',args.output/f'menu-{run}.png')
    results.append(record)
    (args.output/'results.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(record),flush=True)
    if not passed:raise SystemExit(1)
print(f'PASS hidden client {args.mode}', flush=True)
