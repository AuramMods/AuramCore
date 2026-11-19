# Shared surface map

AuramCore captures decorated terrain at Minecraft’s FEATURES stage. Ordinary chunk generation and Distant Horizons in INTERNAL_SERVER mode use this stage. The capture copies the surface of the supplied chunk. A bounded worker assembles, compresses, and writes the tiles without posting chunk tickets. The server also fills missing coverage on natural loads and batches refreshes after surface edits.

The server stores tiles under `auram-surface-map` in the world directory. Each tile contains up to 64 chunks in an 8×8 square and can be sent with partial coverage. Client caches live under `auram-surface-cache` in the instance directory, keyed by the server’s world ID and dimension. The client downloads and caches each tile once. JourneyMap and FTB Chunks load visible pixels from that cache into their own textures. Either mapper can run independently.

On a participating server, both minimaps and fullscreen maps show the same surface layer underground and at night. The same pixels are available in the FTB Chunks claim screen. The Nether and other ceiling dimensions sample beneath the roof. Each mapper keeps its local surface fallback for uncovered chunks and skips its local terrain scans or rasterizer for imported chunks.

FTB Chunks uses its native claim controls, team colors, borders, force-load markers, and map icons. The shared tiles contain pixels and coverage. FTB Chunks can show elevation at positions with local height data, while a location known only from a streamed tile displays an unknown elevation.

## Server settings

Before starting the server, edit `world/serverconfig/auram-server.toml`, using the actual world directory if it has a different name.

```toml
[surfaceMap]
enabled = true
nearbyTilesPerSecond = 10
backgroundTilesPerSecond = 1.0
```

The visible minimap, fullscreen map, or claim screen takes priority. When both minimaps are visible, the client requests their combined area through one stream. The client keeps prefetching outside the viewport in the current dimension while the fullscreen map is closed. The server applies the rates per player, and the background rate accepts fractional values. Both mappers use their normal mapping behavior when the server disables streaming or does not support it.

## JourneyMap validation

Live fixtures used Minecraft 1.20.1, Forge 47.4.10, JourneyMap 5.10.3, and Distant Horizons 2.4.5-b. The full Auram dedicated server wrote tiles in the Overworld, Nether, End, and Aether. With no players connected, a DH pregen run at block (2048, 2048) produced six partial surface tiles in that area. The focused client rendered a separate live DH pregen tile at chunk (128, 128) with vanilla view distance set to four. The JourneyMap texture pixel matched the server tile and remained available after reconnect without another tile body for that coordinate.

| Server configuration | Fullscreen tiles/sec | Map-closed background tiles/sec |
| --- | ---: | ---: |
| 10 nearby, 1 background | 9.88 over 45 tiles | 0.99 over 10 tiles |
| 4 nearby, 0.5 background | 4.00 over 45 tiles | 0.50 over 14 tiles |

Panning at fixed zoom promoted the new viewport and started receiving its tiles in about 182 ms. With the test player at Y=8 in a closed cave at midnight, both views still reported the day surface layer and cave mapping was disabled. A runtime probe observed 69 covered-chunk decisions that skipped local terrain rasterization, with zero local renders reported for those covered chunks in the sample.

The live client switched from the Overworld to the Nether and End and back. After a surface edit at block (16, 319, 128), the server advanced the tile from revision 135 to 136 and the client displayed the new gold pixel.

A separate full-pack client attempt timed out during Forge login before it reached a map session. The full-pack server was used for the dimension and generation checks, and the wire measurements used an isolated client and server with the pack’s installed JourneyMap and Distant Horizons builds.

## Dedicated server comparison

Both arms loaded copies of the same saved world with Java 17, four processors reported to the JVM, and a 6 GiB heap. A smoke test requested 48 cold Overworld chunks through asynchronous region tickets, with 12 outstanding at a time. The measurement starts at the first request and ends when the last chunk is available.

| First run | Elapsed | Chunks/sec | p95 tick | p99 tick |
| --- | ---: | ---: | ---: | ---: |
| Mapping disabled | 119.85 s | 0.400 | 3.28 ms | 9.85 ms |
| Mapping enabled | 156.10 s | 0.307 | 4.69 ms | 20.60 ms |

The enabled run persisted all 48 requested chunks. Four ticks in that run took more than a second. The first comparison measures the effect on the entire workload, including the base pack and JVM, so a second run records GC pauses and times the map capture itself.

| Instrumented repeat | Elapsed | Chunks/sec | p95 tick | p99 tick |
| --- | ---: | ---: | ---: | ---: |
| Mapping enabled | 158.00 s | 0.304 | 3.82 ms | 12.40 ms |
| Mapping disabled | 297.25 s | 0.161 | 4.72 ms | 22.86 ms |

The repeat ran in reverse order with JFR and GC logging. Across 173 main-thread capture calls, mapping took 23.40 ms in total and 2.78 ms for the longest call. G1 recorded full collections in both arms. The longest was 2.32 s with mapping enabled and 10.58 s with mapping disabled. The direction of the throughput difference changed between pairs, so these runs do not establish a stable throughput cost.

## FTB Chunks validation

The isolated client and dedicated server ran Forge 47.4.10, FTB Chunks 2001.3.6, FTB Library 2001.2.12, FTB Teams 2001.3.1, JourneyMap 5.10.3, and Distant Horizons 2.4.5-b. With no players online, DH pregen produced an Overworld tile at chunk (128, 128). Both mappers displayed its server pixel `ff30996d` while the client chunk was unloaded. The client downloaded that revision once while switching between the two maps. A separate JourneyMap-only client displayed the controlled remote tile at chunk (512, 512), with the matching pixel `ffa01739` and no loaded chunk.

FTB displayed the covered pixel from a tile with eight populated chunks. An unloaded chunk in the same tile remained blank. The claim screen reflected claim, force-load, unload, and unclaim through FTB's native packet flow. It showed the team-colored border and the force-load indicator over the shared terrain. A gold block edit at (32, 319, 112) advanced the tile from revision 76 to 77. FTB displayed the new pixel `ff42cdd7` from Y=8 at midnight with its Night mode selected. The client switched from the Overworld to the Nether and End and back. The Nether and End each displayed their own generated tiles.

### Tile delivery

| Client | Server rates (visible/background) | Visible tiles/sec | Background tiles/sec with map closed |
| --- | --- | ---: | ---: |
| FTB Chunks and JourneyMap | 10 / 1 | 9.71 over 184 tiles | 1.00 over 25 tiles |
| FTB Chunks only | 4 / 0.5 | 4.00 over 184 tiles | 0.50 over 13 tiles |

The measurements count tile bodies, including any emitted for partial coverage. The FTB fullscreen map followed the new viewport after a same-zoom pan. The two-mapper run received each observed tile revision once.

### Dedicated server comparison

Each arm ran the same full-pack fixture, input world, Java 17, four JVM processors, and a 6 GiB heap. The workload requested 48 cold Overworld chunks in batches of up to 12 asynchronous region tickets. Both arms used server mapping and persisted 10 tiles for the workload.

| AuramCore build | Chunks/sec | p50 tick | p95 tick | p99 tick |
| --- | ---: | ---: | ---: | ---: |
| 1.0.18 with JourneyMap integration | 0.576 | 1.31 ms | 4.41 ms | 16.78 ms |
| With FTB Chunks integration | 0.608 | 1.28 ms | 5.17 ms | 21.65 ms |

Both JVMs recorded full G1 collections. The map capture ran 73 times on the main thread in each arm, taking 25.09 ms in total on the baseline and 12.23 ms in the candidate. The FTB integration runs on the client and shares the existing capture and upload code. This comparison measures cold generation and server ticks with mapping active and no connected players. It does not measure the CPU cost of rendering the maps on a client.
