# Four-CPU JVM comparison

Retain the current JVM settings. Ten completed full-pack JVM runs did not establish a repeatable throughput and latency improvement from the tested alternatives. The code patches from cycles 1–3 remain independently validated; this cycle supplies the reproducible tuning harness and evidence.

| Configuration | Requested FULL chunks/s (runs 1, 2) | p95 server tick, seconds (runs 1, 2) | Actual workers |
| --- | ---: | ---: | ---: |
| `captured` | 1.482, 1.196 | 5.371, 5.885 | 15 |
| `g1_defaults` | 1.375, 1.583 | 5.932, 4.990 | 15 |
| `workers_2` | 1.232, 1.099 | 6.820, 7.263 | 2 |
| `workers_3` | 1.319, 1.329 | 6.032, 5.713 | 3 |
| `initial_heap_4g` | 1.435, 1.225 | 5.735, 5.996 | 15 |

GC pauses totaled at most **0.70%** of any measured workload; the largest individual pause was **73.5 ms**, versus a largest chunk-loading tick of **32.91 s**. These pauses do not explain the long stalls in this fixture. The G1-default repeat is faster while the captured-flags repeat is slower; averaging them would conceal that reversal. Two workers regress in both rounds; neither a larger initial heap nor three workers establishes a repeated win.

The ten JVMs complete 200 warmup and 800 measured **requested** FULL chunks, plus their generation dependencies. Measurements and paired-round comparisons are in `cycle4-summary.json`. Per-process `cycle4-<configuration>-<round>.json` files retain commands, actual runtime jar hashes and workload metrics. `cycle4-environment.json` records the frozen input hashes, rechecked after the runs.

## Workload and controls

The benchmark uses a frozen full ServerPackCreator export (Imperium 1.0.1 and Satiscraftory 1.0.0, before the separate pack update to Satiscraftory 1.1.0), the patched AuramCore 1.0.6 jar, Forge 47.4.10, Java 17 and seed 104729. Each JVM starts with its own fresh world and writable copy of the same input files. Exported Auram jars are replaced by mod ID, avoiding duplicates after versioned jar renames. Mods that process their own files cannot change the frozen template or other runs.

The isolated Ubuntu 24.04 VM has four vCPUs and 24 GiB RAM. World data is on its native ext4 disk; only mod/config inputs and result files use the shared host filesystem. No unrelated Docker VM, live pack, hosted server or existing save is modified.

Every process warms up with five batches of four FULL chunks, then measures twenty batches of four FULL chunks. Coordinates, request order and world seed are identical. Minecraft's `getChunkFuture` blocks when called on the server thread: this scenario deliberately measures those stalls, rather than an asynchronous pregenerator's maximum throughput. Four chunk requests form each measured batch.

Saved FULL chunks contain 68 block sections spanning Y=-64 through Y=1023. Every configuration preserves this terrain height. The forced origin remains loaded. Once per tick, the test serializes 128 ArmorStand objects with the installed mods' real capabilities and compares held-item NBT. The objects are not added to the world's entity index: full-pack chunk visibility without connected players differed from the minimal fixture. This explicit, fixed workload avoids comparing different entity counts. It is a synthetic serialization load, not an active factory or connected player simulation.

Startup, warmup, final block fingerprinting and shutdown are excluded from workload timings. Mixin class export is disabled. A separate test mod supplies instrumentation and never ships in the production jar.

## Configurations

All configurations retain `-Xmx12000M`, the limit captured in `server3`, and explicitly select G1. Two JVMs run per configuration; the second round rotates the original four configurations. A three-worker follow-up runs after that matrix because the first two-worker trial did not improve throughput.

| Name | Change |
| --- | --- |
| `captured` | The complete historical GC flag bundle, with unspecified initial heap as captured |
| `g1_defaults` | G1 defaults, unspecified initial heap |
| `workers_2` | G1 defaults plus a disposable `threadtweak.json` override setting `threadCount.main` to 2 |
| `workers_3` | G1 defaults plus a disposable override setting `threadCount.main` to 3 |
| `initial_heap_4g` | G1 defaults plus `-Xms4G` |

The complete flag bundle is in `tools/compare_tuning.py`, not reconstructed from thread counts. The frozen pack sets ThreadTweak's `threadCount.main` to 15; its replacement executor ignores vanilla's `max.bg.threads` property. The worker case therefore changes ThreadTweak's actual config in the private fixture, leaving the frozen template and production files intact. The harness records the actual background executor parallelism. ForkJoin compensation threads do not prove a matching number of simultaneously active workers.

## Metrics

- FULL chunks per second and median/p95 batch duration.
- Main server tick median, p95, p99 and maximum, including chunk waits; process and server-thread CPU time are separate.
- Allocated-byte delta summed over live Java threads. Threads that terminate inside the measurement interval can make this an underestimate.
- Background pool size/active counts sampled at tick end can miss activity during a long chunk wait.
- Heap occupancy sampled at tick end. This can miss peaks during a long blocked tick and is not a retained-heap measurement.
- JFR GC pause count, total, p95 and maximum. Empty samples do not establish that GC is unimportant in an existing save.
- JFR sampled allocation classes/owners. First samples for each thread are excluded because their weights can include allocations before recording began. Sampled weights are estimates, not exact allocation totals.
- Generated block-state fingerprints outside the timing window, to expose output differences or worldgen variability. `tools/compare_saved_chunks.py` compares saved palette names/properties independently of numeric registry IDs. The first GC-only pair differed at 45,062 of 22,282,240 block positions (about 0.2%); `cycle4-canonical-comparison.json` records examples. The identical G1-default repeats also differed at 42,980 positions in 56 chunks, confirming baseline output variability; see `cycle4-repeat-variability.json`.

Differences below 5% are inconclusive. A settings recommendation requires repeated throughput and latency improvements; a single favorable average is insufficient.

## Reproduce

Build only the reobfuscated artifacts. `build` also triggers this repository's live-pack deployment task.

```sh
./gradlew reobfJarJar reobfSmokeTestJar
python3 tools/compare_tuning.py \
  --java /path/to/java17/bin/java \
  --libraries /path/to/forge-server/libraries \
  --server-template /path/to/frozen/server-export \
  --runtime-parent /path/to/disposable/runs \
  --output /path/to/results --rounds 2
```

Run sequentially on a dedicated four-CPU environment. `--resume` explicitly reuses completed per-process result files; use it only with the same frozen input, artifacts and settings. This run retained the completed captured/default measurements when the ThreadTweak override was added, before any worker-case process was launched. On the ARM host used here, the Java executable is a small Lima shim that runs `/usr/bin/java` inside the four-vCPU VM and places each world's directory on guest ext4. The environment JSON records the official Lima download and checksum. A native Linux host needs no shim or Lima dependency. The runner requires the installed modpack path in `gradle.properties` for its existing dependency resolver.

The test writes `tuning-benchmark.json`, `tuning.jfr` and a pass/fail marker in each printed runtime directory. The Python runner requires actual process success and the completed workload marker. Console logs remain alongside the runtime for failures.

## Limits

The captured hosted machine had four AMD x86 CPUs, Java 17.0.10 and approximately 9.4 GiB heap in use. This VM uses ARM CPUs and Java 17.0.21. Relative local comparisons are useful screening evidence; they do not establish the hosted machine's best settings.

A fresh playerless world does not reproduce the retained heap, player functions, factories, chunk unloading or connected-client load of the hosted save. In particular, these measurements cannot justify reducing its heap limit. Retest promising settings on the actual host with the retained save before changing production flags. The code optimizations from cycles 1–3 have their own command/packet/terrain/RNG equivalence evidence and do not depend on this settings experiment.
