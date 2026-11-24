# Overworld generation and tick measurements

The starting Spark capture was `0Ait4Y42Zl.sparkprofile`. Worldgen workers spent 49.5 sampled seconds in terrain noise fill, 30.8 seconds constructing noise samplers across stages and 3.4 seconds in Better Caves' fluid replacement. Datapack commands and Immersive Weathering growth used 19% and 5% of active server tick samples, respectively. Single-player pauses affected the original throughput measurement.

## Confirmed changes

| Workload | Control | Candidate |
| --- | ---: | ---: |
| Better Caves cached liquid lookup, median | 12.13 ns | 7.79 ns |
| Better Caves cached liquid lookup, p95 | 12.17 ns | 11.80 ns |
| Immersive Weathering block tick with no growth rule, median | 17.49 ns | 10.23 ns |
| Immersive Weathering block tick with no growth rule, p95 | 20.32 ns | 11.73 ns |
| Serializations in the four-arrow Guns fixture | 6 | 4 |

The Better Caves replays matched all saved block states, biome palettes, structure starts and fluid ticks across two pairs of 48 chunks before decoration. The Weathering fixture ran universal and block-specific rules in order with one shared biome supplier and the same number of RNG draws. The Guns fixture selected the same sole matching arrow, with the same result order.

The selector trace counted 219 global world queries in one invocation of `ggunz:loop`, including its nested functions. It also found 30 in `scorched:tick` and 27 in `incendium:clocks/main`. The Guns cleanup keeps the original kill command and combines its two filters on the same arrow NBT. Some of the broader commands repeat a selector to form execution contexts at multiple positions. Changing that sequence would change projectile or effect behavior.

## Rejected density mapping experiments

Memoizing all binary operands doubled the median time for a warmed 16-column Overworld batch in the initial replay. Reusing the full final-density branch still gave slower column medians in the two follow-up pairs. In the 48-chunk negative-coordinate replay, full branch reuse gave 7.13 fresh chunks/s against 10.19/s in the control. The saved terrain, biomes, structures and fluid ticks matched.

## Verified sampler reuse

The climate change reuses the six mapped climate functions from the same noise chunk for eligible unblended routers. Two Overworld control runs used 2.12 and 1.98 seconds of aggregate biome-stage CPU. Two candidate runs used 1.25 and 1.21 seconds, roughly 40% lower on average. All four runs produced the same fingerprint across 73,728 biome samples.

A separate 48-chunk cold replay from chunk (-92, -90) completed at 10.00 fresh FULL chunks/s on the control and 11.70/s on the candidate. Biome-stage CPU was 1.25 versus 0.67 seconds. Tick p95 was 19.10 versus 14.72 ms, and p99 and maximum were 50.27 versus 47.61 ms. All 4,718,592 pre-decoration block states, biome palettes, structure starts and saved fluid ticks matched. These rates come from a four-worker headless fixture with Tectonic, Lithostitched, ModernFix, Ksyxis and Better Caves.

The Nyctophobia predicate matched its original height test in all 144 cases, including four occupied-height-64 boundary cases. When a matching terrain cell was already in scope, those checks created zero additional samplers.

## Integrated Distant Horizons

The fixture is a frozen copy of a new full-pack save with seed 104729. It runs Forge 47.4.10 on Java 17 with a fixed 60 FPS cap, shaders off, render and simulation distance 5, and a 64-chunk DH radius. The clone contains Distant Horizons 3.3.3, AuramCore 1.0.33 and Thaumaturge 1.1.4. It uses the production `INTERNAL_SERVER` chunk pipeline and DH's native health guard.

The first 100-second Balanced run used eight workers and reached nine in-flight batches. It completed 15 existing FULL chunks and zero fresh FULL chunks in the measured window. Its tick p95 was 89.54 ms, p99 was 148.15 ms, and longest tick was 7,732.03 ms. A thread dump during the plateau showed all eight DH workers waiting for their futures while Minecraft workers were still filling terrain, aquifers and features.

The matched Overworld runs use eight workers on Balanced and twelve on Aggressive. Rates use the interval between the first and last ten-second diagnostic reports. Tick percentiles cover the full 145-second measurement.

| Overworld mode | Peak in-flight batches | Fresh FULL/s | LOD source chunks/s | Tick p95 | Tick p99 | Longest tick |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Balanced, native capacity | 9 | 1.15 | 0.91 | 80.92 ms | 156.23 ms | 345.46 ms |
| Aggressive, native capacity | 13 | 3.05 | 2.85 | 25.76 ms | 54.85 ms | 162.84 ms |
| Aggressive, two-batch limit | 2 | 1.18 | 1.83 | 21.93 ms | 47.18 ms | 144.07 ms |

The Balanced repeat completed 161 fresh FULL chunks, 55 existing FULL chunks and 128 chunks of LOD source data in 140.10 seconds. The repeat progressed further than the initial 100-second Balanced run, in which the startup plateau occupied the entire measurement.

Native capacity finished 427 fresh FULL chunks, 55 existing FULL chunks and 400 chunks of LOD source data over 140.15 seconds. The cap finished 165 fresh FULL chunks, 91 existing FULL chunks and 256 chunks of LOD source data over 140.21 seconds. The cap reduced p95 by 15% and p99 by 14%, with 61% lower fresh throughput and 36% lower LOD source throughput. The pack keeps native capacity by default. Set `-Dauram.dh.overworldBatches=2` to favor lower tick latency in the integrated Overworld.

At shutdown, the capped run still had 32 observed FULL requests pending. DH reported zero incomplete generator tasks after closing the queues. The cap only gates new submissions. DH retains ownership of ticket creation, completion and cancellation.

The Aether Balanced trial crossed into fresh terrain at block (4096, 200, 4096) with eight workers. Over 90.07 seconds of diagnostics it finished 3,582 fresh FULL chunks and 225 existing FULL chunks, equivalent to 39.77 fresh chunks/s and 2.50 existing chunks/s. It completed LOD source data for 3,760 chunks at 41.74/s. Its tick p95 was 38.95 ms, p99 was 66.75 ms, and longest tick was 128.51 ms. The mean observed FULL latency was 0.76 seconds in the Aether and 27.49 seconds in the uncapped Aggressive Overworld. These rates describe different terrain workloads. Existing chunks alone account for only a small part of the Aether rate.
