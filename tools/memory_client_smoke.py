#!/usr/bin/env python3
"""Run the memory/rendering suite using the cached Forge launch and a private game directory."""

import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import threading
import time


ROOT = Path(__file__).resolve().parents[1]
PACK = Path("/Users/cyberpwn/Library/Application Support/PrismLauncher/instances/Auram")
RUNTIME = Path("/Users/cyberpwn/Library/Application Support/AuramLauncherDev")


def copy_private(source, target):
    if sys.platform == "darwin":
        if ctypes.CDLL(None).clonefile(os.fsencode(source), os.fsencode(target), 0) == 0:
            return str(target)
    return shutil.copy2(source, target)


def set_options(path, separator, overrides):
    lines = path.read_text().splitlines() if path.exists() else []
    lines = [line for line in lines if line.split(separator, 1)[0].strip() not in overrides]
    lines.extend(key + separator + value for key, value in overrides.items())
    path.write_text("\n".join(lines) + "\n")


def prepare(args, game, smoke):
    game.mkdir(parents=True)
    for name in ("mods", "config", "defaultconfigs", "kubejs", "scripts", "resourcepacks",
                 "globalresources", "moonlight-global-datapacks", "patchouli_books",
                 "modernfix", "quests_structures", "shaderpacks"):
        source = args.pack / "minecraft" / name
        if source.is_dir():
            shutil.copytree(source, game / name, copy_function=copy_private,
                            ignore=shutil.ignore_patterns("spark", "crash-reports"))
    for path in (game / "mods").glob("AuramCore-*.jar"):
        path.unlink()
    (game / "mods" / "Auram.jar").unlink(missing_ok=True)
    copy_private(args.jar, game / "mods" / "AuramCore-memory-fixture.jar")
    copy_private(smoke, game / "mods" / "auram-memory-smoke.jar")

    for name in ("BSL_v10.1.zip", "BSL_v10.1.zip.txt"):
        target = game / "shaderpacks" / name
        if not target.exists():
            with target.open("wb") as stream:
                subprocess.run(["git", "show", f"HEAD:minecraft/shaderpacks/{name}"],
                               cwd=args.pack, stdout=stream, check=True)
    copy_private(args.pack / "minecraft/options.txt", game / "options.txt")
    set_options(game / "options.txt", ":", {
        "soundCategory_master": "0.0", "pauseOnLostFocus": "false", "enableVsync": "false",
        "maxFps": "60", "renderDistance": "5", "simulationDistance": "5", "fullscreen": "false",
    })
    set_options(game / "config/fml.toml", " = ", {"earlyWindowControl": "false", "versionCheck": "false"})
    set_options(game / "config/oculus.properties", "=", {"shaderPack": "BSL_v10.1.zip", "enableShaders": str(args.shaders).lower()})
    if args.mode == "memory-derender":
        path = game / "config/DistantHorizons.toml"
        # The fixed arena exercises client rendering independently of distant world generation.
        text = path.read_text()
        path.write_text(text.replace("enableDistantGeneration = true", "enableDistantGeneration = false"))


def sample_native(process, game, output, bin_dir):
    if sys.platform != "darwin":
        return
    checkpoints = ("startup", "reloaded", "post-full-gc")
    pending = dict.fromkeys(checkpoints)
    while process.poll() is None and pending:
        for name in list(pending):
            if not (game / f"combined-{name}.txt").exists():
                continue
            del pending[name]
            with (output / f"vmmap-{name}.txt").open("w") as stream:
                subprocess.run(["vmmap", "-summary", str(process.pid)],
                               stdout=stream, stderr=subprocess.STDOUT, timeout=20)
            if name == "post-full-gc":
                with (output / "jvm-native-memory.txt").open("w") as stream:
                    subprocess.run([str(bin_dir / "jcmd"), str(process.pid), "VM.native_memory", "summary"],
                                   stdout=stream, stderr=subprocess.STDOUT, timeout=20)
        time.sleep(0.2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, default=PACK)
    parser.add_argument("--runtime", type=Path, default=RUNTIME)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--smoke-jar", type=Path, help="Use an explicitly built test jar for both sides of an A/B run")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--mode", required=True)
    parser.add_argument("--xmx", default="12G")
    parser.add_argument("--xms", default="2G")
    parser.add_argument("--property", action="append", default=[])
    parser.add_argument("--profile-bin", type=Path, help="JDK bin directory for runtime heap and native samples")
    parser.add_argument("--timeout", type=int, default=720)
    parser.add_argument("--shaders", action="store_true", help="Enable BSL for the full route")
    args = parser.parse_args()
    output = args.output.resolve()
    game = output / "minecraft"
    version = next(line.partition("=")[2] for line in (ROOT / "gradle.properties").read_text().splitlines()
                   if line.startswith("mod_version="))
    smoke = args.smoke_jar or ROOT / f"build/libs/auram-{version}-smoke-test.jar"
    prepare(args, game, smoke)

    base = shlex.split((args.runtime / "state/launch-jvm.args").read_text())
    base = [value for value in base if not value.startswith(("-Xm", "-XX:", "-javaagent:", "-Dauram.branding."))]
    flags = [f"-Xms{args.xms}", f"-Xmx{args.xmx}", "-XX:+UseG1GC", "-XX:NativeMemoryTracking=summary",
             "-Xlog:gc*:file=gc.log:uptimemillis,tags", "-Dauram.smoke.hiddenWindow=true",
             f"-Dauram.smoke={args.mode}", *["-D" + pair for pair in args.property]]
    if args.profile_bin:
        flags.append("-XX:StartFlightRecording=settings=profile,filename=memory-run.jfr,dumponexit=true")
    game_args = ["--username", "AuramQA", "--uuid", "c7dad607797a4dd7b7f41d6d187aa2bc", "--accessToken", "0",
                 "--userType", "mojang", "--version", "1.20.1-forge-47.4.10", "--versionType", "release",
                 "--gameDir", str(game), "--assetsDir", str(args.runtime / "cache/minecraft/assets"),
                 "--assetIndex", "5", "--launchTarget", "forgeclient", "--fml.forgeVersion", "47.4.10",
                 "--fml.mcVersion", "1.20.1", "--fml.forgeGroup", "net.minecraftforge", "--fml.mcpVersion",
                 "20230612.114412", "--width", "854", "--height", "480"]
    argfile = output / "client.args"
    argfile.write_text("\n".join(json.dumps(value) for value in base[:-1] + flags + base[-1:] + game_args) + "\n")
    start = time.monotonic()
    with (output / "console.log").open("w") as log:
        process = subprocess.Popen([str(args.java), "@" + str(argfile)], cwd=game, stdout=log, stderr=subprocess.STDOUT)
        print(f"Hidden Minecraft PID {process.pid}. Logs in {output}", flush=True)
        jstat = native = None
        if args.profile_bin:
            with (output / "jstat-gc.txt").open("w") as stream:
                jstat = subprocess.Popen([str(args.profile_bin / "jstat"), "-gc", "-t",
                                          str(process.pid), "250"], stdout=stream, stderr=subprocess.STDOUT)
            native = threading.Thread(target=sample_native, args=(process, game, output, args.profile_bin), daemon=True)
            native.start()
        try:
            exit_code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise SystemExit(f"Client timed out. See {output / 'console.log'}")
        finally:
            if jstat and jstat.poll() is None:
                jstat.terminate()
                jstat.wait(timeout=10)
            if native:
                native.join(timeout=5)
    passed = exit_code == 0 and (game / f"{args.mode}-pass.txt").is_file()
    summary = {"passed": passed, "exit_code": exit_code, "elapsed_seconds": time.monotonic() - start,
               "core_sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
               "smoke_sha256": hashlib.sha256(smoke.read_bytes()).hexdigest(), "mode": args.mode,
               "heap": {"min": args.xms, "max": args.xmx}}
    (output / "run.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary), flush=True)
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
