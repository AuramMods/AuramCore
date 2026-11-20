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
