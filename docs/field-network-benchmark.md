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

## 1.0.43 baseline

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

The baseline logged a pre-existing invalid `getFuelModule` shadow in the Tinkers
heating-controller mixin. The harness still reached both rendering clients and all
sampling phases. Treat heating-controller coverage separately from the other Tinkers
fixture blocks until that mixin is corrected.

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
