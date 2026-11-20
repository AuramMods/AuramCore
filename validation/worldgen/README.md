# World generation benchmarks

The fixture runs the production-mapped smoke mod on a dedicated Forge server with a disposable world. It uses the pack's ServerPackCreator export, real spawn preparation and the normal asynchronous chunk ticket pipeline. Fresh worlds and frozen pack inputs are shared across comparisons by configuration and seed.

Build only the benchmark and production jars so the build never installs a test artifact:

```sh
./gradlew reobfJarJar reobfSmokeTestJar
python3 tools/performance_smoke.py --no-build --mode worldgen-bench \
  --java /path/to/java17 --libraries /path/to/forge-libraries \
  --server-template /path/to/private/server-export --runtime-parent /path/to/runs \
  --seed 104729 --xms 4G --xmx 10G --no-mixin-export \
  --java-arg=-XX:ActiveProcessorCount=4 --java-arg=-Dauram.smoke.stageTimings=true
```

Use a four-CPU VM or server and run comparisons sequentially. Each run measures world loading to spawn-ready and the spawn preparation call separately. It collects individual cold-chunk request-to-FULL latencies for 48 targets beginning at chunk (88, 88), with at most 12 requests pending. Override the dimension with `-Dauram.smoke.dimension=aether:the_aether` or `-Dauram.smoke.dimension=minecraft:the_nether`. The spawn region always uses the Overworld. The fixture records worker CPU and the counted stages separately for startup and the cold workload. The timing includes overhead from the smoke hooks equally in both arms.

The first cycle adds `-Dauram.smoke.aquiferReuse=true` to count existing per-sampler hits, time actual scans, and observe exact repeated integer results across independent samplers. The observation table is bounded and collisions can undercount repeats. Blended samplers are excluded from reuse counts. The observer always executes the original query and does not modify its output. Use stage timings without the detailed observer for throughput comparisons.

```sh
python3 tools/worldgen_bench_summary.py /path/to/run-1 /path/to/run-2
python3 tools/compare_saved_chunks.py /path/to/run-1/world /path/to/run-2/world \
  --positions /path/to/run-1/worldgen-chunks.csv --output /path/to/parity.json
```

Point the comparator to the corresponding dimension directory when measuring another dimension. It decodes canonical biome and block palettes, including fluid blocks and waterlogged states, and compares complete structure starts and saved fluid ticks. In full-pack comparisons, establish baseline repeatability before attributing differences to a patch.

## Aquifer probe pilot

The first production-mapped observer run completed all 48 cold FULL targets on the four-vCPU VM using AuramCore 1.0.20. Cold starts, noise fill and carving spent 36.85 s, 35.17 s and 9.39 s of thread CPU in the original preliminary scans respectively. Those scans accounted for 46%, 49% and 66% of their measured stage CPU. The observer found 3,722 repeat scans during starts, 2,324 during noise fill and 535 during carving. All observed repeat heights matched exactly.

This initial run used a packed coordinate hash that made adjacent diagonals collide. The repeat counts demonstrate reuse but understate its rate. The probe now mixes the 64-bit coordinates before looking up the observation slot. The staged timings include observer overhead and cannot establish candidate throughput. Ksyxis completed spawn-region preparation in about 0.16 s. The separate Forge Done timer was 132.38 s. A thread dump after that timer showed WorldEdit building its block-state index before cold requests began.

The corrected probe completed 48 cold FULL targets and recorded 65.02 s of preliminary scan CPU. It observed 36,617 repeat scans out of 47,369 in structure starts, 27,549 out of 29,502 in noise fill, and 8,334 out of 8,738 in carving. The repeat rates were 77%, 93%, and 95%. Carving and noise fill saw most repeat scans last computed by a different worker. All repeat heights agreed. The largest remaining opportunities are shared by the three stages.

Comparing saved output from both original-jar runs found 42,770 differing blocks out of 13,369,344, and biome differences in 25 of 48 chunks. All saved structure starts and fluid ticks agreed. The exact height oracle and deterministic target-specific fixtures supplement comparison of the full pack.

## Exact aquifer surface-height reuse

The cache stores integer preliminary heights in a bounded table owned by the seeded `RandomState`. Each sampler uses its own noise graph and local caches. Reuse requires enabled aquifers, an unblended sampler, matching effective height settings, and a supported preliminary density graph.

Use the same hashed smoke jar and four-CPU settings on both arms. The oracle explicitly recomputes every observed preliminary height and should run separately from throughput measurement:

```sh
python3 tools/performance_smoke.py --no-build --mode aquiferparity \
  --java /path/to/java17 --libraries /path/to/forge-libraries \
  --baseline-jar /path/to/core-variant.jar --smoke-jar /path/to/smoke.jar \
  --with-mod tectonic --with-mod lithostitched --no-mixin-export \
  --java-arg=-XX:ActiveProcessorCount=4 --java-arg=-Dauram.smoke.verifyAquiferHeights=true
```

Compare `aquifer-parity.txt` for the seeded height fingerprint, `aquifer-fallback-parity.txt` for blended and unsupported sampler cases, and `worldgen-stage-cpu.csv` for lookups, hits, and verified heights. For an independent saved-world replay, use `worldgen-bench` with the same mods plus `bettercaves` and `ksyxis`. Keep the oracle enabled on both variants and compare the saved region files.

The full-pack run order was candidate, control, control, candidate. All runs used seed 104729, 48 cold FULL targets, and the same four-CPU limits and instrumentation.

| Run | World load to spawn-ready (s) | Cold FULL chunks/s | p95 chunk-ready (s) | Cold starts CPU (s) | Cold noise CPU (s) | Cold carving CPU (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Control A1 | 81.11 | 0.544 | 69.95 | 49.30 | 40.89 | 9.51 |
| Candidate B1 | 59.09 | 0.487 | 90.36 | 51.17 | 19.31 | 3.00 |
| Control A2 | 71.38 | 0.302 | 136.42 | 95.14 | 37.95 | 8.76 |
| Candidate B2 | 44.02 | 0.305 | 136.53 | 69.38 | 40.84 | 6.39 |

World load to spawn-ready improved by 22.02 and 27.36 seconds. The same 529 startup structure tasks used 30.69 and 26.01 seconds of worker CPU on the controls and 17.88 and 14.25 seconds on the candidates. Cold carving CPU improved in each pair. Cold throughput, p95 and cold noise CPU varied across pairs. The candidate shared cache hit 73–74% of cold start lookups and 94–96% of cold noise and carving lookups.

The two identical full-pack controls yielded 45,426 block differences across 13,369,344 positions, and biome differences in 25 of 48 chunks. Their structure starts and fluid ticks matched. The first full-pack candidate replay differed in 55,127 block states and 31 biome palettes against the first control. The Trek fort at chunk (93, 91) was present in both controls. At its projected start (1480, 80, 1463), the saved biome was `biomesoplenty:field` in the controls and `environmental:pine_barrens` in the first candidate. The fort's biome tag admits the field and excludes pine barrens. The second candidate matched every saved biome, structure start, and fluid tick from the first control, with 24,726 block differences. The fixture option `-Dauram.smoke.orderBlueprintSlices=true` can hold the order of Blueprint's weighted biome slices fixed across a separate full-pack replay.

The minimal fixture with Tectonic, Lithostitched and Better Caves agreed with the uncached oracle on 23,233 height checks in each arm. All 16 saved biome palettes, structure starts and fluid tick lists matched. The 128 block-state differences out of 1,572,864 positions were concentrated in stone variants, dripstone, ore and foliage. The separate direct fallback replay matched three seeds and confirmed zero shared lookups for the clipped height range, legacy terrain blending and an unfamiliar Y-dependent density function.

With Blueprint's weighted slice order fixed in the test mod, the full-pack replay matched all 16 biome palettes, complete structure starts and fluid tick lists. Both arms placed the same five-piece Trek fort. The control and candidate verified 94,995 and 87,444 uncached heights respectively with zero mismatches. The saved blocks still differed at 8,637 of 4,456,448 positions, including ore and foliage.

Use `--java-arg=-Dauram.smoke.compareBeforeDecoration=true` in the minimal replay to isolate saved output after terrain, surface and carving. The smoke mod then skips decoration in the disposable worlds. This replay matched all 1,572,864 block states, biome palettes, complete structure starts and fluid ticks across all 16 chunks.

The cold JFR recordings contained 715 and 738 Better Caves stack samples across the controls and 311 and 401 across the candidates. Preliminary height queries accounted for 445 and 445 control samples and 20 and 33 candidate samples. The residual samples were dominated by Better Caves noise evaluation. The overlap accounts for the observed reduction without an independent carver change.

## Aether Bronze Dungeon terrain probes

Use the `bronzeparity` fixture with `--with-mod aether` to compare the immediate aquifer baseline, the tunnel and room changes alone, and the combined variant with the four-corner search. The test calls Aether's structure code across 192 positions on three seeds. Its comparison includes placement decisions, start coordinates, serialized piece content, RNG draw counts, and the next RNG value. It canonicalizes piece ordering because Aether builds from identity-keyed adjacency maps. All six matched trials produced 23 accepted starts and the same 665 pieces.

| Variant | Median candidate search CPU in each trial (ms) | Median accepted dungeon CPU (ms) | Median rejected dungeon CPU (ms) |
| --- | --- | --- | --- |
| Aquifer baseline | 5.75, 6.00 | 13.06, 13.90 | 5.73, 5.67 |
| Tunnel and room probes | 5.59, 5.26 | 5.58, 5.89 | 5.59, 5.25 |
| Tunnel, room and four-corner probes | 2.43, 2.24 | 10.61, 6.28 | 2.16, 2.14 |

The combined variant preserves the inclusive vertical bounds, probe order and original thickness and tie rules. The largest benefit from the four-corner change is for rejected starts.

## TerraBlender region IDs

The `regionparity` fixture runs seeded biome fills on a dedicated server with four CPUs. Use `--with-mod terrablender --with-mod deep_aether --java-arg=-Dauram.smoke.dimension=aether:the_aether` for Aether and `--with-mod terrablender --with-mod biomesoplenty --java-arg=-Dauram.smoke.dimension=minecraft:the_nether` for the Nether. Enable `auram.smoke.stageTimings` and `auram.smoke.targetWorldgen` on both arms to measure locks. Each run compares 49,152 biome positions across 48 chunks. All four fingerprints matched within each dimension.

| Dimension | Baseline median wall time per 48 fills (ms) | Candidate median wall time (ms) | Baseline read-lock acquisitions | Candidate read-lock acquisitions | Aggregate off-CPU acquisition time baseline → candidate (ms) |
| --- | --- | --- | --- | --- | --- |
| Aether | 40.3, 46.2 | 17.7, 16.9 | 204,827, 205,481 | 9,341, 9,299 | 251.9 → 9.1, 301.0 → 39.3 |
| Nether | 62.4, 46.9 | 50.2, 19.4 | 204,884, 205,706 | 9,301, 9,309 | 278.9 → 56.2, 245.5 → 29.4 |

The read-lock count represents original area lookups, including recursive lookups. The off-CPU estimate subtracts thread CPU from acquisition wall time and includes scheduling and instrumentation overhead. Write-lock counts and per-stage CPU are in the cycle report. The cache holds the area identity and quart X/Z within one fill. The original climate sampler still evaluates Y-dependent values and biome selection.

## Dreamtinker scatter ore

The `scatterparity` fixture with `--with-mod dreamtinker --with-mod immersiveengineering` exercises the registered `dreamtinker:shadow_silver_ore` feature against seeded layouts of stone, tagged silver, and existing ore. Each arm includes 120 measured placements. All four arms produced the same 360 block writes in the same order, placement decisions, RNG draw counts and next RNG values.

Aggregate feature CPU fell from 642 to 529 ms in the pair with tracing disabled and from 299 to 229 ms in the pair with tracing enabled. The instrumented process-wide count, including startup and warm-up, fell from 2,990,613 original two-tag calls to 4,208 across the same 534 feature calls. The cache lives within one feature invocation and applies to the exact Dreamtinker two-tag rule.
