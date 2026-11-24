# Overworld generation at Y=1024

## Bounded interpolation for structure probes

The original tall fixture showed substantial work in structure probes for Nether fossils and Malum wells. The implementation keeps the full noise shape and aquifer context and prepares interpolation through the highest cell each query can reach. A wider follow-up query gets a fresh full sampler. The implementation limits the new path to supported seeded density graphs, including eager flat-cache values at the four lattice points a single-cell probe reads.

The released AuramCore 1.0.35 jar was the baseline. Tests used Forge 47.4.10, Java 17.0.18, seed 104729, four visible CPUs, and the pack's Tectonic settings from Y=-64 to Y=1024. The headless fixture loaded Tectonic, Lithostitched, Better Caves, ModernFix, Ksyxis, Nyctophobia, Biomes O' Plenty, and Malum with their dependencies.

The paired `boundedslice` smoke test used five warmup pairs and 25 measured pairs, with 16 fossil-style searches per arm in each pair. The bounded path matched 102,384 raw double values and 22,116 block states. It exercised 45 bounded samples and 45 later full-height requests. All 400 timed search results per arm agreed. Thread CPU fell in all 25 measured pairs. The median fell from 104.91 ms to 88.43 ms per batch.

The cold chunk fixture used the same mod set, 32 vanilla tickets at chunk origin (-26, 12), and 100 warmup ticks. Feature decoration was bypassed in all four arms for the saved-world comparison. All four runs completed 736 structure-start tasks and 96 noise fills.

| Arm | Fresh FULL/s | Chunk ready median | Chunk ready p95 | Structure starts CPU | NOISE CPU |
| --- | ---: | ---: | ---: | ---: | ---: |
| A1 released jar | 1.40 | 4.55 s | 15.40 s | 4.27 s | 10.94 s |
| B1 bounded | 2.13 | 2.90 s | 11.80 s | 3.53 s | 9.91 s |
| B2 bounded | 2.18 | 2.62 s | 10.95 s | 3.89 s | 9.97 s |
| A2 released jar | 1.71 | 3.90 s | 13.90 s | 4.31 s | 10.80 s |

Both comparisons matched 8,912,896 saved blocks, biome palettes, complete structure starts and fluid ticks across all 32 targets. Time in the unchanged NOISE stage also varied, so the whole-fixture throughput figures describe the individual runs. The paired probe CPU figures isolate the change.

The initial conservative classifier declined Tectonic's vertical functions even inside already-populated flat caches. The test failed because the new path was never exercised. The final classifier checks eager inputs separately and declines unknown functions and deferred interpolation or cell fillers there.

## Spline sampling allocations

The full-pack JFR had sampled allocations of `DensityFunctions.Spline.Point` in the NOISE stage. The wrapper contains the function context, and ordinary NoiseChunk sampling already updates and reuses that context. The change creates the wrapper once per NoiseChunk and preserves the normal wrapper construction for other contexts.

The isolated comparison used the four-CPU cold fixture above in both run orders. Every arm completed 96 noise fills and 736 structure-start tasks. Both 32-chunk pairs matched all 8,912,896 blocks, biome palettes, starts and fluid ticks. JFR results were filtered to each run's timed phase and to stacks in the noise stage.

| Arm | Weighted spline wrapper allocation samples | All weighted allocation samples in NOISE | NOISE CPU |
| --- | ---: | ---: | ---: |
| A1 released jar | 506.24 MB | 2,427.81 MB | 10.33 s |
| B1 reused wrapper | 0 sampled | 1,909.75 MB | 10.98 s |
| B2 reused wrapper | 2.10 MB | 1,919.70 MB | 10.28 s |
| A2 released jar | 582.83 MB | 2,363.07 MB | 10.20 s |

The allocation figures are estimates from weighted samples. Throughput varied with run order. The repeatable improvement is the removal of nearly all sampled allocations at that site during noise fill.

## Empty structure density modifiers

The full-pack recording showed boxing and callback allocations inside Beardifier during noise fill. Vanilla, Lithostitched, Integrated API and YUNG's API build the structure terrain contributions before sampling a chunk. AuramCore uses a stateless zero-density modifier when the vanilla and enhanced iterators are all empty and the chunk has no active terrain blending. The replacement keeps the original density bounds and default array fill. Nonempty or unrecognized modifiers use the original implementation.

The 32-target fixture loaded both terrain-adaptation APIs. All 32 targets referenced an ancient city. Both run orders matched 8,912,896 blocks, biome palettes, structure starts and fluid ticks across the saved worlds. NOISE CPU for 96 fills varied from 11.45 to 12.48 seconds in the controls and 11.65 to 12.50 seconds in the candidates. Sampled Beardifier allocation weights fell from 598.57 to 496.86 MB in the first pair and from 555.53 to 532.42 MB in the reverse pair. Host contention varied and the wall rates in this round cannot isolate the effect of the patch.

A second pair kept both APIs loaded and disabled structure generation in the disposable fixture to isolate empty modifiers. Each arm completed 72 noise fills and the same 16 requested chunks. All 4,456,448 saved block states, biome palettes, structure starts and fluid ticks matched. JFR attributed 522.17 MB of weighted NOISE allocations to Beardifier in the control and sampled none in the candidate. Total weighted allocations in NOISE fell from 1,721.40 MB to 1,296.38 MB. CPU was 8.27 and 8.36 seconds, respectively. These tests support an allocation reduction, with the largest effect in terrain without nearby contributing structures.

The first injection used a generated Mixin argument class and failed on the Forge class loader. The tested single-argument injection avoids that generated class.
