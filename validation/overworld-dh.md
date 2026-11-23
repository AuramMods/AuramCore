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

Memoizing all binary operands doubled the median time for a warmed 16-column Overworld batch in the initial replay. Reusing the full final-density branch still gave slower column medians in the two follow-up pairs. In the 48-chunk negative-coordinate replay, full branch reuse gave 7.13 fresh chunks/s against 10.19/s in the control. The saved terrain, biomes, structures and fluid ticks matched. The remaining climate sampler candidate is measured separately from those changes.

## Integrated Distant Horizons

The fixture is a frozen copy of a new full-pack save with seed 104729. It runs Forge 47.4.10 on Java 17 with a fixed 60 FPS cap, shaders off, render and simulation distance 5, and a 64-chunk DH radius. It uses the production `INTERNAL_SERVER` chunk pipeline and DH's native health guard.

The first 100-second Balanced run used eight workers and reached nine in-flight batches. It completed 15 existing FULL chunks and zero fresh FULL chunks in the measured window. Its tick p95 was 89.54 ms, p99 was 148.15 ms, and longest tick was 7,732.03 ms. A thread dump during the plateau showed all eight DH workers waiting for their futures while Minecraft workers were still filling terrain, aquifers and features.

The Aggressive pair used twelve workers in both runs. Rates use the interval between the first and last ten-second diagnostic reports. Tick percentiles cover the full 145-second measurement.

| Overworld Aggressive | Peak in-flight batches | Fresh FULL/s | LOD source chunks/s | Tick p95 | Tick p99 | Longest tick |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Native capacity | 13 | 3.05 | 2.85 | 25.76 ms | 54.85 ms | 162.84 ms |
| Two-batch limit | 2 | 1.18 | 1.83 | 21.93 ms | 47.18 ms | 144.07 ms |

Native capacity finished 427 fresh FULL chunks, 55 existing FULL chunks and 400 chunks of LOD source data over 140.15 seconds. The cap finished 165 fresh FULL chunks, 91 existing FULL chunks and 256 chunks of LOD source data over 140.21 seconds. The cap reduced p95 by 15% and p99 by 14%, with 61% lower fresh throughput and 36% lower LOD source throughput. The pack keeps native capacity by default. Set `-Dauram.dh.overworldBatches=2` to favor lower tick latency in the integrated Overworld.

At shutdown, the capped run still had 32 observed FULL requests pending. DH reported zero incomplete generator tasks after closing the queues. The cap only gates new submissions. DH retains ownership of ticket creation, completion and cancellation.
