#!/usr/bin/env python3
"""Launch Java directly in a private full-pack copy, with/without Forge's early window.

Uses an existing JVM-only argfile and cached runtime/libraries; never invokes a launcher.
Authentication is an offline QA identity. Live saves and settings are never modified.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, default=Path('/Users/cyberpwn/Library/Application Support/PrismLauncher/instances/Auram'))
parser.add_argument('--runtime', type=Path, default=Path('/Users/cyberpwn/Library/Application Support/AuramLauncherDev'))
parser.add_argument('--java', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--case', choices=['early-agent', 'late-agent', 'core-fallback'], action='append', help='Run only selected cases (default: all).')
args = parser.parse_args()
args.output = args.output.resolve()
game = args.output/'minecraft'
game.mkdir(parents=True, exist_ok=True)
version = next(s.split('=', 1)[1] for s in (ROOT/'gradle.properties').read_text().splitlines() if s.startswith('mod_version='))

def copy_private(source, target):
    if sys.platform == 'darwin' and ctypes.CDLL(None).clonefile(os.fsencode(source), os.fsencode(target), 0) == 0:
        return str(target)
    return shutil.copy2(source, target)

for name in ['mods', 'config', 'defaultconfigs', 'kubejs', 'scripts', 'resourcepacks', 'globalresources',
             'moonlight-global-datapacks', 'patchouli_books', 'modernfix', 'quests_structures', '.connector']:
    source = args.pack/'minecraft'/name
    if source.is_dir() and not (game/name).exists():
        shutil.copytree(source, game/name, copy_function=copy_private)
for path in (game/'mods').glob('AuramCore-*.jar'): path.unlink()
(game/'mods/Auram.jar').unlink(missing_ok=True)
shutil.copy2(ROOT/f'build/libs/auram-{version}-all.jar', game/'mods'/f'AuramCore-{version}.jar')
shutil.copy2(ROOT/f'build/libs/auram-{version}-smoke-test.jar', game/'mods/auram-branding-smoke.jar')
shutil.copytree(ROOT/'branding', game/'branding', dirs_exist_ok=True)
shutil.copy2(ROOT/'bootstrap/build/libs/auram-bootstrap.jar', game/'branding/auram-bootstrap.jar')
base = shlex.split((args.runtime/'state/launch-jvm.args').read_text())
base = [a for a in base if not a.startswith(('-Xm', '-XX:', '-javaagent:', '-Dauram.branding.'))]
base[-1:-1] = ['-Xms1G', '-Xmx20G', '-Dauram.smoke=branding', '-Dauram.smoke.hiddenWindow=true']
game_args = ['--username', 'AuramQA', '--uuid', 'c7dad607797a4dd7b7f41d6d187aa2bc', '--accessToken', '0',
             '--userType', 'mojang', '--version', '1.20.1-forge-47.4.10', '--versionType', 'release',
             '--gameDir', str(game), '--assetsDir', str(args.runtime/'cache/minecraft/assets'), '--assetIndex', '5',
             '--launchTarget', 'forgeclient', '--fml.forgeVersion', '47.4.10', '--fml.mcVersion', '1.20.1',
             '--fml.forgeGroup', 'net.minecraftforge', '--fml.mcpVersion', '20230612.114412', '--width', '854', '--height', '480']
results = []
for label, early, agent, external in [('early-agent', True, True, True), ('late-agent', False, True, True), ('core-fallback', False, False, False)]:
    if args.case and label not in args.case: continue
    for name in ['auram-branding-pass.txt', 'auram-branding-failed.txt', 'branding-results.json']:
        (game/name).unlink(missing_ok=True)
    config = game/'config/fml.toml'
    text = config.read_text()
    config.write_text('\n'.join(f'earlyWindowControl = {str(early).lower()}' if line.startswith('earlyWindowControl =') else line for line in text.splitlines())+'\n')
    (game/'options.txt').write_text('guiScale:2\nrenderDistance:2\nsimulationDistance:2\nsoundCategory_master:0.0\n')
    flags = []
    if external: flags += ['-Dauram.branding.name=Auram', '-Dauram.branding.icon='+str(game/'branding/icon.png')]
    if agent: flags += ['-javaagent:'+str(game/'branding/auram-bootstrap.jar'), '-Dauram.smoke.expectBootstrap=true']
    jvm = base[:-1] + flags + base[-1:]
    argfile = args.output/(label+'.args')
    argfile.write_text('\n'.join(json.dumps(s) for s in jvm+game_args)+'\n')
    start = time.monotonic()
    print('Launching Java directly: '+label, flush=True)
    with (args.output/(label+'.log')).open('w') as log:
        process = subprocess.Popen([str(args.java), '@'+str(argfile)], cwd=game, stdout=log, stderr=subprocess.STDOUT)
        try: code = process.wait(timeout=420)
        except subprocess.TimeoutExpired:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
            raise SystemExit('Direct client timed out: '+label)
    passed = code == 0 and (game/'auram-branding-pass.txt').exists()
    record = dict(case=label, passed=passed, exit_code=code, elapsed_seconds=time.monotonic()-start)
    if (game/'branding-results.json').exists(): record['checks'] = json.loads((game/'branding-results.json').read_text())
    if (game/'auram-branding-failed.txt').exists(): record['failure'] = (game/'auram-branding-failed.txt').read_text()
    results.append(record)
    (args.output/'results.json').write_text(json.dumps(results, indent=2)+'\n')
    print(json.dumps(record), flush=True)
    if not passed: raise SystemExit('Direct client failed: '+label)
print('PASS direct Java client launches and native title/icon checks', flush=True)
