"""Replay the field traffic fixture on a dedicated Forge server and two hidden Bliss clients."""
import argparse
from contextlib import ExitStack
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import uuid

SHARED_MODS = (
    "Botania-1.20.1-450-FORGE.jar", "Patchouli-1.20.1-84.1-FORGE.jar",
    "curios-forge-5.14.1+1.20.1.jar", "Mantle-1.20.1-1.11.97.jar",
    "TConstruct-1.20.1-3.11.2.166.jar", "open_blocks-1.0.0.jar",
    "Mekanism-1.20.1-10.4.16.80.jar", "enderio-8.2.22-beta.jar",
)
TEST_OPTION = "-Dauram.smoke=field-network"
PLAYER_NEAR, PLAYER_FAR = "FieldNear", "FieldFar"
PROBE = "QA_EVT "


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    parser.add_argument("core", type=Path, help="Reobfuscated server jar")
    parser.add_argument("probe", type=Path, help="Reobfuscated smoke-test jar")
    parser.add_argument("java", type=Path, help="Native JDK 17 executable")
    parser.add_argument("work", type=Path, help="New directory for the server, clients, and logs")
    parser.add_argument("--client-core", type=Path, help="Different Core jar for both clients")
    parser.add_argument("--seconds", type=float, default=10, help="Duration of each sample window")
    return parser.parse_args()


def wait_for(log, child, marker, timeout=120, offset=0):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        with log.open(errors="replace") as stream:
            stream.seek(offset)
            if marker in stream.read():
                return
        if child.poll() is not None:
            raise RuntimeError(f"Process exited with {child.returncode}. See {log}")
        time.sleep(0.25)
    raise TimeoutError(f"Timed out waiting for {marker!r} in {log}")


def launch(stack, command, work, *, console_input=False, env=None):
    log = work / "console.log"
    output = stack.enter_context(log.open("w"))
    child = subprocess.Popen(command, cwd=work, stdout=output, stderr=subprocess.STDOUT,
                             stdin=subprocess.PIPE if console_input else subprocess.DEVNULL,
                             env=env)
    stack.callback(stop, child)
    return child, log


def stop(child):
    if child.poll() is not None:
        return
    if child.stdin is not None:
        try:
            send(child, "stop")
            child.wait(timeout=25)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    child.terminate()
    try:
        child.wait(timeout=10)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait()


def send(server, *commands):
    server.stdin.write(("\n".join(commands) + "\n").encode())
    server.stdin.flush()


def server_at(args, stack, port, jars):
    work = args.work / "server"
    mods = work / "mods"
    mods.mkdir(parents=True)
    for jar in jars:
        shutil.copy2(jar, mods / jar.name)
    (work / "libraries").symlink_to(args.pack / "server/libraries", target_is_directory=True)
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-seed=663494\n"
        "level-type=minecraft:flat\nspawn-protection=0\nview-distance=7\nsimulation-distance=6\n")
    forge = "@libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt"
    return launch(stack, [str(args.java), "-Xms1g", "-Xmx4g", TEST_OPTION, forge, "nogui"],
                  work, console_input=True)


def client_at(args, stack, port, jars, name):
    work = args.work / name
    work.mkdir()
    dummy = work / "empty"
    dummy.mkdir()
    spec = importlib.util.spec_from_file_location("bliss_qa", args.pack / "tools/aurambliss-qa/run.py")
    qa = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(qa)
    previous_argv = sys.argv
    sys.argv = [previous_argv[0], str(args.pack), str(dummy), "--work", str(work),
                "--shader", str(args.pack / "minecraft/shaderpacks/AuramBliss"), "--java", str(args.java),
                "--max-fps", "30", "--heap", "4g", "--companion",
                str(args.pack / "minecraft/mods/auramphoton-compat-0.1.0-dh333.jar"),
                f"--java-option={TEST_OPTION}", "--java-option=-Dforge.disableVersionCheck=true"]
    for jar in jars:
        sys.argv.extend(("--probe", str(jar)))
    try:
        profile = qa.parse_args()
    finally:
        sys.argv = previous_argv
    game = qa.prepare(profile, work)
    (game / "config/forge-client.toml").write_text("showLoadWarnings=false\n")
    (game / "shaderpacks/AuramBliss.txt").write_text(
        "VOLUMETRIC_VAPOR=true\nRF_FLOW_STREAKS=true\n")
    command = qa.launch_command(profile, work, game)
    quickplay = command.index("--quickPlaySingleplayer")
    command[quickplay:quickplay+2] = ["--quickPlayMultiplayer", f"127.0.0.1:{port}"]
    command[command.index("--username")+1] = name
    command[command.index("--uuid")+1] = uuid.uuid5(uuid.NAMESPACE_DNS, name).hex
    arguments_file = work / "client.args"
    arguments_file.write_text("\n".join(map(json.dumps, command[1:])) + "\n")
    return launch(stack, [command[0], f"@{arguments_file}"], work)


def build(server, log):
    send(server, "gamerule doFireTick false", "gamerule doDaylightCycle false",
         "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "time set 6000",
         "weather clear", "forceload add 16 16 63 63", "forceload add 190 34 210 52",
         "setworldspawn 35 149 32", "say FIELD_CHUNKS_QUEUED")
    wait_for(log, server, "FIELD_CHUNKS_QUEUED")
    time.sleep(2)
    send(server, "fill 20 147 17 61 147 52 minecraft:polished_andesite",
         "fill 20 148 35 26 152 43 minecraft:stone hollow",
         "fill 23 148 35 23 150 35 minecraft:air",
         "setblock 25 148 40 minecraft:netherrack",
         "setblock 21 148 39 minecraft:soul_soil",
         "setblock 23 148 42 minecraft:soul_campfire[lit=false]",
         "setblock 24 148 38 minecraft:lava_cauldron",
         "setblock 22 148 40 minecraft:furnace[facing=north]",
         "setblock 43 147 43 minecraft:dirt",
         "setblock 43 148 43 botania:endoflame",
         "setblock 44 148 41 botania:mana_pool{mana:250000}",
         "setblock 45 148 43 open_blocks:fan",
         "data merge block 45 148 43 {yaw:90.0f}",
         "fill 27 147 44 31 147 48 tconstruct:seared_bricks",
         "fill 27 148 44 31 148 48 tconstruct:seared_bricks hollow",
         "setblock 29 148 44 tconstruct:smeltery_controller[facing=north]",
         'setblock 27 148 46 tconstruct:seared_fuel_tank{tank:{FluidName:"minecraft:lava",Amount:800}}',
         "setblock 28 148 44 tconstruct:seared_glass",
         'setblock 33 148 44 tconstruct:seared_ingot_tank{tank:{FluidName:"tconstruct:molten_cobalt",Amount:3500}}',
         "setblock 33 148 43 tconstruct:seared_faucet[facing=north]",
         "setblock 33 147 43 tconstruct:seared_casting_tank",
         'setblock 34 148 45 tconstruct:seared_channel{tank:{FluidName:"tconstruct:molten_copper",Amount:60}}',
         'setblock 35 148 44 tconstruct:seared_basin{tank:{capacity:810,fluid:{FluidName:"tconstruct:molten_copper",Amount:690}}}',
         "fill 190 147 34 210 147 52 minecraft:stone", "setblock 199 148 42 minecraft:netherrack",
         "setblock 199 149 42 minecraft:fire")
    send(server, 'setblock 43 148 28 enderio:creative_power[facing=east]'
         '{IOConfig:{Modes:{east:"push"}},RedstoneControl:"active_with_signal"}',
         "setblock 49 148 24 enderio:vibrant_capacitor_bank",
         "setblock 53 148 28 enderio:vibrant_capacitor_bank",
         "fill 44 148 28 52 148 28 enderio:conduit",
         "fill 49 148 25 49 148 27 enderio:conduit",
         "fill 40 148 22 46 148 22 mekanism:ultimate_universal_cable",
         "fill 43 148 19 43 148 21 mekanism:ultimate_universal_cable",
         "setblock 39 148 22 mekanism:ultimate_energy_cube[facing=east]",
         "setblock 47 148 22 mekanism:ultimate_energy_cube[facing=east]",
         "setblock 43 148 18 mekanism:ultimate_energy_cube[facing=north]",
         "say FIELD_TOPOLOGY_PLACED")
    wait_for(log, server, "FIELD_TOPOLOGY_PLACED")
    time.sleep(2)
    send(server, "fieldconduits")
    wait_for(log, server, "FIELD_CONDUITS_READY", timeout=15)
    send(server, 'data merge block 29 148 44 {tank:{capacity:11520,fluids:[{FluidName:"tconstruct:molten_cobalt",Amount:7200}]}}')


def toggle(server, active):
    send(server, "fieldfixtures " + str(active).lower(),
         "setblock 25 149 40 minecraft:" + ("fire" if active else "air"),
         "setblock 21 149 39 minecraft:" + ("soul_fire" if active else "air"),
         "setblock 23 148 42 minecraft:soul_campfire[lit=" + str(active).lower() + "]",
         "setblock 43 148 29 minecraft:" + ("redstone_block" if active else "air"),
         "setblock 46 148 43 minecraft:" + ("redstone_block" if active else "air"),
         "setblock 34 148 43 minecraft:" + ("redstone_block" if active else "air"),
         "data merge block 43 148 43 {burnTime:" + ("200000" if active else "0") + "}",
         'data merge block 22 148 40 {Items:[{Slot:0b,id:"minecraft:iron_ore",Count:64b},'
         '{Slot:1b,id:"minecraft:coal",Count:64b}],BurnTime:20s}' if active else
         "data merge block 22 148 40 {Items:[],CookTime:0s,BurnTime:0s}")


def read_samples(log):
    return [json.loads(line.split(PROBE, 1)[1]) for line in log.read_text(errors="replace").splitlines()
            if PROBE in line and '"event":"field_network_phase"' in line]


def main():
    args = arguments()
    for field in ("pack", "core", "probe", "java", "work", "client_core"):
        if (path := getattr(args, field)) is not None:
            setattr(args, field, path.resolve())
    args.work.mkdir()
    os.environ["PATH"] = str(args.java.parent) + os.pathsep + os.environ["PATH"]
    mod_dir = args.pack / "minecraft/mods"
    pack_jars = [mod_dir / name for name in SHARED_MODS]
    with socket.socket() as reserved:
        reserved.bind(("127.0.0.1", 0))
        port = reserved.getsockname()[1]
    with ExitStack() as stack:
        server, server_log = server_at(args, stack, port, [args.core, args.probe, *pack_jars])
        wait_for(server_log, server, "Done (")
        build(server, server_log)
        client_jars = [args.client_core or args.core, args.probe, *pack_jars]
        clients = {}
        for player, position in ((PLAYER_NEAR, "36.5 153 31.5 0 27"), (PLAYER_FAR, "201.5 151 39.5 0 20")):
            child, log = client_at(args, stack, port, client_jars, player)
            clients[player] = (child, log)
            wait_for(server_log, server, f"{player} joined the game", timeout=240)
            send(server, f"gamemode spectator {player}", f"tp {player} {position}")
            wait_for(log, child, "Creating pipeline for dimension", timeout=120)
        time.sleep(6)
        send(server, "fieldnet idle")
        time.sleep(args.seconds)
        send(server, "fieldnet starting")
        toggle(server, True)
        time.sleep(3)
        send(server, "fieldnet steady")
        time.sleep(args.seconds)
        send(server, "fieldnet stopping")
        toggle(server, False)
        time.sleep(3)
        send(server, "fieldnet stopped")
        time.sleep(args.seconds)
        send(server, "fieldnet done")
        for child, log in clients.values():
            wait_for(log, child, '"phase":"stopped"', timeout=20)
        measurements = {"server": read_samples(server_log)}
        measurements.update({name: read_samples(log) for name, (_, log) in clients.items()})
        result = args.work / "metrics.json"
        result.write_text(json.dumps(measurements, indent=2) + "\n")
        print(result)


if __name__ == "__main__":
    main()
