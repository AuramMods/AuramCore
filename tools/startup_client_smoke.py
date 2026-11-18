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
parser.add_argument('--runs', type=int, default=3)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
version = next(line.split('=', 1)[1] for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
artifact = ROOT/f'build/libs/auram-{version}-all.jar'
smoke = ROOT/f'build/libs/auram-{version}-smoke-test.jar'
instance_id = 'Auram-Zeta-QA-' + uuid.uuid4().hex[:8]
instance = args.pack.parent/instance_id
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
overrides = {'name': instance_id, 'uuid': str(uuid.uuid4()), 'JvmArgs': '"-XX:+UseG1GC -Dauram.smoke=startup -Dauram.smoke.hiddenWindow=true"',
             'OverrideJavaArgs': 'true', 'OverrideMemory': 'true', 'MinMemAlloc': '2048', 'MaxMemAlloc': '20000',
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
shutil.copy2(artifact, game/'mods'/f'AuramCore-{version}.jar')
shutil.copy2(smoke, game/'mods/auram-startup-smoke.jar')
fml=game/'config/fml.toml'
text=fml.read_text()
text='\n'.join('earlyWindowControl = false' if line.startswith('earlyWindowControl =') else line for line in text.splitlines())+'\n'
fml.write_text(text)

def java_pids():
    result=[]
    for line in subprocess.check_output(['ps','-axo','pid=,command='],text=True).splitlines():
        if instance_id in line and 'org.prismlauncher.EntryPoint' in line:
            result.append(int(line.split(None,1)[0]))
    return result

results=[]
summary={'instance':str(instance),'artifact_sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),
         'mod_jars':len(list((game/'mods').glob('*.jar'))),'runs':results}
print('Fixture: '+str(instance),flush=True)
for run in range(1,args.runs+1):
    for marker in ['auram-startup-client-pass.txt','auram-startup-client-failed.txt','startup-client-results.json']:
        (game/marker).unlink(missing_ok=True)
    shutil.copy2(args.pack/'minecraft/options.txt',game/'options.txt')
    log=game/'logs/latest.log'
    if log.exists(): log.unlink()
    start=time.monotonic()
    subprocess.run([args.prism,'--launch',instance_id,'--offline','AuramQA'],check=True,
                   stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    seen=False; reason=None
    while time.monotonic()-start<360:
        alive=java_pids();seen=seen or bool(alive)
        if log.exists():
            data=log.read_text(errors='replace')
            if 'Failed to complete lifecycle event CONSTRUCT' in data:
                reason='mod construction failed'
                for pid in alive: os.kill(pid,signal.SIGTERM)
        terminal = (game/'auram-startup-client-pass.txt').exists() or (game/'auram-startup-client-failed.txt').exists()
        if not alive and (seen or terminal):break
        time.sleep(1)
    else:
        reason='client timeout'
        for pid in java_pids():os.kill(pid,signal.SIGTERM)
        for _ in range(20):
            if not java_pids():break
            time.sleep(1)
        for pid in java_pids():os.kill(pid,signal.SIGKILL)
    passed=(game/'auram-startup-client-pass.txt').exists() and reason is None
    record={'run':run,'passed':passed,'elapsed_seconds':time.monotonic()-start,'reason':reason}
    if (game/'startup-client-results.json').exists():record['checks']=json.loads((game/'startup-client-results.json').read_text())
    if (game/'auram-startup-client-failed.txt').exists():record['failure']=(game/'auram-startup-client-failed.txt').read_text()
    if log.exists():shutil.copy2(log,args.output/f'client-{run}.log')
    if (game/'startup-menu.png').exists():shutil.copy2(game/'startup-menu.png',args.output/f'menu-{run}.png')
    results.append(record)
    (args.output/'results.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(record),flush=True)
    if not passed:raise SystemExit(1)
print('PASS repeated hidden client startup and resource reload',flush=True)
