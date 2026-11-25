# Field network benchmark

Run the fixture against a released or candidate reobfuscated AuramCore jar using the Auram
pack checkout and a Java 17 JDK. The pack supplies the Forge dedicated server libraries,
pinned integration mods, shader, and hidden-window test launcher.

```sh
./gradlew smokeTestJar
python3 tools/field_network_smoke.py \
  "$PACK" "$AURAM_CORE_JAR" build/libs/auram-1.0.43-smoke-test.jar "$JAVA_HOME/bin/java" "$RUN"
```

Use the actual smoke-test jar filename for the checked-out version. The script creates
`$RUN`, logs each process separately, and writes `metrics.json`. It launches two muted
clients that connect over loopback TCP to a disposable dedicated Forge server. Use
`--client-core` to test a different client version. The near client watches the active
fixtures and the far client watches an independent fire source. Both sample idle,
starting, steady, stopping, and stopped windows. Tick durations measure wall-clock
time between Forge tick callbacks. Channel counts include the Forge discriminator
and payload and exclude Minecraft packet framing and compression.

## Initial 1.0.43 measurement

Forge 47.4.10, Apple M4 Max, Java 17.0.18, Oculus 1.8.0, Embeddium 0.3.31, Distant
Horizons 3.3.3, AuramBliss, 854×480, 30 FPS cap. Installed AuramCore jar SHA-256
`e22f65d2c2826fd86ad401db9a1d6090d78297aee8bf8566e0d992f88dd309de`.

| Steady window, 199 server ticks | Near packets | Near payload bytes | Far packets | Far payload bytes |
| --- | ---: | ---: | ---: | ---: |
| Vapor | 50 | 15,650 | 5 | 230 |
| Wind | 5 | 295 | 0 | 0 |
| Light | 5 | 195 | 0 | 0 |
| RF | 50 | 8,900 | 0 | 0 |
| Total | 110 | 25,040 | 5 | 230 |

| Steady tick duration | Median | p95 |
| --- | ---: | ---: |
| Dedicated server | 2.172 ms | 3.524 ms |
| Near client | 0.975 ms | 1.436 ms |
| Far client | 1.013 ms | 1.662 ms |

The initial fixture's Ender IO banks filled partway through the run, which left
Mekanism as the sustained RF load. The final fixture drains the two Ender IO
receivers and caps each terminal conduit, so both branches continue to carry
traffic. Both benchmark jars use that final topology for the comparison.

The baseline logged a pre-existing invalid `getFuelModule` shadow in the Tinkers
heating-controller mixin. The harness still reached both rendering clients and all
sampling phases. The machine cycle corrected the shadow mapping and kept the
heating controller on authoritative vapor data.

## Machine state used by the client

- Ender IO publishes its operating `powered` block state for the SAG Mill,
  Alloy Smelter, Slice and Splice, Painting Machine, and Soul Engine. The client
  uses loaded block entities and resolves the same top or front outlet against
  its local world. Machines without a matching published state retain server
  updates.
- Tinkers synchronizes smeltery bounds and contents, casting recipes and
  cooling timers, and channel fluid. These drive the local emitters. A faucet
  can remain visually pouring while the output accepts zero fluid. Faucet smoke
  therefore follows the server's accepted fill. The heater and heating
  controllers also retain server state because their synced tags carry fluid and
  inventory without the solid-fuel module's burn state.
- Thermal's Stirling Dynamo hook observes energy actually generated on that
  tick. Titanium detects the type of fuel consumed on the server. The Extra
  Utilities generators hook actual output. Thaumaturge's alembic smoke and
  infusion intensity depend on server processing and stability. Their updates
  use the sparse emitter channel.
- The vanilla furnace hook follows cooking progress and resolves covered
  outlets on the server. Botania mana pool vapor follows actual transfer,
  including acceptance through a Mana Void with a full pool.

## Sustained branching comparison

Both trials used the same fire room, casting and smeltery blocks, Botania pool and
Endoflame, fan, operating SAG Mill, and two active branches on each energy
network. The hidden clients used the same settings as the initial run with
Bliss flood-fill lighting enabled. The steady windows covered 199 server ticks
on 1.0.43 and 200 with the change.

| Effect | 1.0.43 packets | 1.0.43 payload bytes | Updated packets | Updated payload bytes |
| --- | ---: | ---: | ---: | ---: |
| Vapor, near | 50 | 37,150 | 50 | 3,100 |
| Wind, near | 5 | 295 | 0 | 0 |
| Light, near | 5 | 195 | 5 | 195 |
| RF, near | 50 | 17,300 | 5 | 120 |
| Vapor, far | 5 | 230 | 0 | 0 |
| Total | 115 | 55,170 | 60 | 3,415 |

The updated RF channel sent five 24-byte heartbeats during steady flow. The
high-frequency vapor messages in this fixture come from the mana pool's
changing fill and exact transfer-driven emission rate.

| Steady tick duration | 1.0.43 median | 1.0.43 p95 | Updated median | Updated p95 |
| --- | ---: | ---: | ---: | ---: |
| Dedicated server | 2.723 ms | 4.726 ms | 2.013 ms | 2.859 ms |
| Near client | 1.188 ms | 1.562 ms | 0.870 ms | 0.948 ms |
| Far client | 1.256 ms | 1.607 ms | 0.856 ms | 0.950 ms |

Tick times are individual runs on a shared test host. The packet counts are
direct payload measurements. Update both the server and client to use the
filtered and incremental field data. Either side can mix with 1.0.43 over
the legacy protocol.

Enable `--lights --verify-owned --lifecycle` to check source ownership and
the live Bliss field textures, with branch directions, RF subscriptions,
covered furnace outlets, dimension changes, and reconnect. Use
`--thaumaturge --verify-owned` for the Nitor fixture.
