#!/usr/bin/env python3
"""Measure player chunk delivery and DH load with a hidden Forge client on a disposable server."""
import argparse
import csv
import hashlib
import json
import re
import shlex
import shutil
import socket
import sqlite3
import statistics
import subprocess
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = "DistantHorizons.toml"
USERNAME = "AuramQA"
UUID = "c7dad607797a4dd7b7f41d6d187aa2bc"
SERVER_IGNORES = {"libraries", "world", "logs", "crash-reports", "mods", "DistantHorizons.sqlite"}
CLIENT_DIRS = {"resourcepacks", "defaultconfigs", "config", "scripts", "kubejs", "globalresources",
               "moonlight-global-datapacks", "thingpacks"}


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--server-java", type=Path, help="Optional Java wrapper on a separate host")
    parser.add_argument("--endpoint", help="Reachable server address for the client")
    parser.add_argument("--port", type=int)
    parser.add_argument("--libraries", required=True, type=Path)
    parser.add_argument("--pack", required=True, type=Path, help="Path to the pack's minecraft folder")
    parser.add_argument("--launcher-cache", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--server-template", type=Path)
    parser.add_argument("--server-core", type=Path, help="Override the test server's AuramCore jar for A/B comparison")
    parser.add_argument("--full-client", action="store_true")
    parser.add_argument("--rate", required=True, type=int)
    parser.add_argument("--seed", type=int, default=104729)
    parser.add_argument("--stationary-ticks", type=int, default=600)
    parser.add_argument("--flight-blocks", type=int, default=800)
    parser.add_argument("--warm", action="store_true", help="Measure a second launch with the same world and LOD caches")
    parser.add_argument("--wire-log", action="store_true", help="Enable Forge packet diagnostics for the fixture")
    parser.add_argument("--defer-dh", action="store_true", help="Try deferring new DH groups while foreground FULL is pending")
    parser.add_argument("--client-java-arg", action="append", default=[], help="Extra client JVM argument")
    return parser.parse_args()


def copy_entries(source, destination, entries):
    for path in source.iterdir():
        if path.name not in entries:
            continue
        target = destination / path.name
        if path.is_dir():
            shutil.copytree(path, target, dirs_exist_ok=True)
        else:
            shutil.copy2(path, target)


def prepare(args, server, client, port):
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines()
                      if "=" in line and not line.startswith("#"))
    version = properties["mod_version"]
    (server / "mods").mkdir(parents=True)
    (client / "mods").mkdir(parents=True)
    if args.server_template:
        copy_entries(args.server_template, server,
                     {entry.name for entry in args.server_template.iterdir()} - SERVER_IGNORES)
    if args.full_client:
        copy_entries(args.pack, client, CLIENT_DIRS)
    for target, source in [(server, args.server_template), (client, args.pack if args.full_client else None)]:
        installed = source / "mods" if source else args.pack / "mods"
        for jar in sorted(installed.glob("*.jar")):
            if jar.name.startswith(("AuramCore-", "Auram.jar")):
                continue
            if source or jar.name.startswith(("DistantHorizons-", "ProjectE-", "Patchouli-", "AkashicTome-")):
                shutil.copy2(jar, target / "mods" / jar.name)
        core = (args.server_core if target == server else None) or ROOT / f"build/libs/auram-{version}-all.jar"
        shutil.copy2(core, target / "mods/AuramCore.jar")
        shutil.copy2(ROOT / f"build/libs/auram-{version}-smoke-test.jar", target / "mods/AuramSmoke.jar")
        config = target / "config"
        config.mkdir(exist_ok=True)
        (config / "fml.toml").write_text("earlyWindowControl = false\nversionCheck = false\n")
        dh = (args.pack / "config" / CONFIG).read_text()
        if target == server:
            dh, count = re.subn(r"(?m)^(\s*generationRequestRateLimit = )\d+", rf"\g<1>{args.rate}", dh)
            if count != 1:
                raise ValueError("Could not locate DH generationRequestRateLimit")
        (config / CONFIG).write_text(dh)
    (server / "libraries").symlink_to(args.libraries.resolve(), target_is_directory=True)
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\n"
        "online-mode=false\nallow-flight=true\nlevel-type=minecraft:normal\n"
        "difficulty=peaceful\ngamemode=creative\nforce-gamemode=true\nspawn-protection=0\n"
        f"level-seed={args.seed}\nmax-tick-time=-1\nview-distance=4\nsimulation-distance=4\n")
    (client / "config/forge-client.toml").write_text("[client]\nshowLoadWarnings = false\n")
    (client / "options.txt").write_text(
        "guiScale:2\nrenderDistance:4\nsimulationDistance:5\nmaxFps:30\n"
        "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\nsoundCategory_master:0.0\n")


def start_client(args, game, log, endpoint):
    base = shlex.split((args.launcher_cache / "state/launch-jvm.args").read_text())
    base = [arg for arg in base if not arg.startswith(("-Xm", "-XX:", "-javaagent:", "-Dauram."))]
    flags = ["-Xms1G", "-Xmx8G" if args.full_client else "-Xmx4G", "-XX:ActiveProcessorCount=4",
             "-Dauram.smoke=dhpriority-client", "-Dauram.smoke.hiddenWindow=true",
             f"-Dauram.dh.endpoint={endpoint}", f"-Dauram.dh.stationaryTicks={args.stationary_ticks}",
             f"-Dauram.dh.flightBlocks={args.flight_blocks}"]
    if args.wire_log:
        flags.append("-Dforge.logging.markers=NETWORK,NETWORK_PACKETS")
    flags.extend(args.client_java_arg)
    game_args = ["--username", USERNAME, "--uuid", UUID, "--accessToken", "0", "--userType", "mojang",
                 "--version", "1.20.1-forge-47.4.10", "--versionType", "release", "--gameDir", str(game),
                 "--assetsDir", str(args.launcher_cache / "cache/minecraft/assets"), "--assetIndex", "5",
                 "--launchTarget", "forgeclient", "--fml.forgeVersion", "47.4.10", "--fml.mcVersion", "1.20.1",
                 "--fml.forgeGroup", "net.minecraftforge", "--fml.mcpVersion", "20230612.114412",
                 "--width", "854", "--height", "480"]
    argfile = game / "launch.args"
    argfile.write_text("\n".join(json.dumps(arg) for arg in base[:-1] + flags + base[-1:] + game_args) + "\n")
    return subprocess.Popen([args.java, "@" + str(argfile)], cwd=game, stdout=log, stderr=subprocess.STDOUT)


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


def wait_ready(server, process):
    deadline = time.monotonic() + 540
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Server exited at startup with code {process.returncode}")
        if 'Done (' in server.read_text(errors="replace"):
            return
        time.sleep(2)
    raise TimeoutError("Server did not finish starting")


def summarize(result):
    def read(name):
        with (result / name).open() as source:
            return [{key: int(value) for key, value in row.items()} for row in csv.DictReader(source)]

    def timings(values):
        if not values:
            return None
        ordered = sorted(values)
        return {label: round(ordered[int((len(ordered)-1) * fraction)] / 1e6, 3)
                for label, fraction in [("p50_ms", .50), ("p95_ms", .95), ("p99_ms", .99), ("max_ms", 1)]}

    ticks = read("dh-server-ticks.csv")
    chunks = read("dh-server-chunks.csv")
    client = read("dh-client.csv")
    center = (ticks[0]["center_x"], ticks[0]["center_z"])
    destination = (ticks[-1]["center_x"], ticks[-1]["center_z"])
    destination_client = (client[-1]["center_x"], client[-1]["center_z"])
    moving = next((row["elapsed_ns"] for row in ticks if (row["center_x"], row["center_z"]) != center), None)
    reached_end = next((row["elapsed_ns"] for row in ticks
                        if (row["center_x"], row["center_z"]) == destination), None)
    flight = [row for row in ticks if moving is not None and moving <= row["elapsed_ns"] < reached_end]
    flight_chunks = [row for row in chunks if moving is not None and moving <= row["requested_ns"] < reached_end]
    visible = [row for row in client if row["visible_buffers"] > 0]
    result = {
        "server_ticks": len(ticks),
        "player_chunk_route": [center, destination],
        "client_destination": destination_client,
        "foreground_observed": len(chunks),
        "foreground_unfinished": sum(row["full_ns"] < 0 for row in chunks),
        "foreground_unsent": sum(row["sent_ns"] < 0 for row in chunks),
        "foreground_newly_reached_full": timings([row["full_ns"] for row in chunks if row["full_ns"] > 0]),
        "foreground_newly_sent": timings([row["sent_ns"] for row in chunks if row["sent_ns"] > 0]),
        "flight_newly_reached_full": timings([row["full_ns"] for row in flight_chunks if row["full_ns"] > 0]),
        "flight_newly_sent": timings([row["sent_ns"] for row in flight_chunks if row["sent_ns"] > 0]),
        "flight_seconds": round((reached_end - moving) / 1e9, 3) if moving is not None else None,
        "tick_times": timings([row["tick_ns"] for row in ticks]),
        "tick_intervals": timings([row["interval_ns"] for row in ticks[1:]]),
        "flight_tick_times": timings([row["tick_ns"] for row in flight]),
        "flight_tick_intervals": timings([row["interval_ns"] for row in flight]),
        "dh_ticket_adds": ticks[-1]["ticket_adds"], "dh_ticket_removals": ticks[-1]["ticket_removals"],
        "peak_dh_tickets": max(row["active"] for row in ticks),
        "peak_dh_waiting_groups": max(row["waiting"] for row in ticks),
        "peak_dh_running_groups": max(row["running"] for row in ticks),
        "deferred_dh_admissions": ticks[-1].get("deferred", 0),
        "peak_visible_buffers": max(row["visible_buffers"] for row in client),
        "end_visible_buffers": client[-1]["visible_buffers"],
        "first_visible_lod_ms": round(visible[0]["elapsed_ns"] / 1e6, 3) if visible else None,
        "flight_mean_loaded_inner_chunks": round(statistics.mean(row["loaded"] for row in client if row["phase"] == 1), 3),
    }
    return result


def lod_sections(databases):
    counts = {}
    for path in databases:
        if not path.is_file():
            continue
        with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as database:
            for level, amount in database.execute("SELECT DetailLevel, COUNT(*) FROM FullData GROUP BY DetailLevel"):
                counts[level] = counts.get(level, 0) + amount
    return counts


def run(args, server, client, port, arm):
    result = args.work / arm
    result.mkdir()
    for directory, names in [(server, ["dh-server-ticks.csv", "dh-server-chunks.csv"]),
                             (client, ["dh-client.csv", "dh-client-pass.txt"])]:
        for name in names:
            (directory / name).unlink(missing_ok=True)
    command = [args.server_java or args.java, "-Xms1G", "-Xmx8G" if args.server_template else "-Xmx4G",
               "-XX:ActiveProcessorCount=4", "-Dauram.smoke=dhpriority-server"]
    if args.defer_dh:
        command.append("-Dauram.dh.defer=true")
    if args.wire_log:
        command.append("-Dforge.logging.markers=NETWORK,NETWORK_PACKETS")
    command += ["@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt", "nogui"]
    server_log, client_log = result / "server.log", result / "client.log"
    with server_log.open("w") as server_output, client_log.open("w") as client_output:
        dedicated = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE,
                                     stdout=server_output, stderr=subprocess.STDOUT)
        player = None
        try:
            wait_ready(server_log, dedicated)
            endpoint = args.endpoint or f"127.0.0.1:{port}"
            player = start_client(args, client, client_output, endpoint)
            client_exit = player.wait(timeout=540)
            if client_exit != 0 or not (client / "dh-client-pass.txt").exists():
                raise RuntimeError(f"Client did not finish route, exit {client_exit}")
            server_exit = dedicated.wait(timeout=180)
            if server_exit != 0:
                raise RuntimeError(f"Server exited with code {server_exit}")
        finally:
            if player:
                stop(player)
            if dedicated.poll() is None:
                try:
                    dedicated.communicate(input=b"stop\n", timeout=60)
                except subprocess.TimeoutExpired:
                    stop(dedicated)
    for name in ["dh-server-ticks.csv", "dh-server-chunks.csv"]:
        shutil.copy2(server / name, result / name)
    shutil.copy2(client / "dh-client.csv", result / "dh-client.csv")
    metrics = summarize(result)
    metrics.update(seed=args.seed, configured_rate=args.rate, server_core_sha256=hashlib.sha256(
        (server / "mods/AuramCore.jar").read_bytes()).hexdigest(),
        server_lod_sections=lod_sections([server / "world/data/DistantHorizons.sqlite"]),
        client_lod_sections=lod_sections(client.glob("Distant_Horizons_server_data/*/*/DistantHorizons.sqlite")))
    (result / "summary.json").write_text(json.dumps(metrics, indent=2) + "\n")
    print(arm, json.dumps(metrics), flush=True)
    if metrics["player_chunk_route"][-1] != metrics["client_destination"]:
        raise RuntimeError("Client and server did not finish in the same chunk")


def main():
    args = parse_args()
    args.work = args.work.resolve()
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", args.port or 0))
        port = sock.getsockname()[1]
    server, client = args.work / "server", args.work / "client"
    prepare(args, server, client, port)
    run(args, server, client, port, "cold")
    if args.warm:
        # Retain the world and both LOD caches, and spawn the test identity at the start again.
        shutil.rmtree(server / "world/playerdata")
        run(args, server, client, port, "warm")


if __name__ == "__main__":
    main()
