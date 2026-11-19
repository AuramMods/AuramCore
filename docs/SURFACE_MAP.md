# Shared JourneyMap surface

AuramCore captures decorated terrain at Minecraft’s FEATURES stage. Ordinary chunk generation and Distant Horizons in INTERNAL_SERVER mode use this stage. The capture copies the surface of the supplied chunk. A bounded worker assembles, compresses, and writes the tiles without posting chunk tickets. The server also fills missing coverage on natural loads and batches refreshes after surface edits.

The server stores tiles under `auram-surface-map` in the world directory. Each tile contains up to 64 chunks in an 8×8 square and can be sent with partial coverage. Client caches live under `auram-surface-cache` in the instance directory, keyed by the server’s world ID and dimension. Only the currently visible tiles need to be loaded into JourneyMap’s textures.

On a participating server, the minimap and fullscreen map show the same surface layer underground and at night. The Nether and other ceiling dimensions sample beneath the roof. JourneyMap maps uncovered surface chunks locally and skips the terrain rasterizer for chunks with imported pixels.

## Server settings

Before starting the server, edit `world/serverconfig/auram-server.toml`, using the actual world directory if it has a different name.

```toml
[surfaceMap]
enabled = true
nearbyTilesPerSecond = 10
backgroundTilesPerSecond = 1.0
```

The visible minimap or fullscreen viewport takes priority. The client keeps prefetching outside the viewport while the fullscreen map is closed. The rates are enforced per player, and the background rate accepts fractional values. A server with `enabled = false` leaves clients on JourneyMap’s normal mapping behavior.

## Validation

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
