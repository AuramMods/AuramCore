# Auram client memory measurements

## Derender Patcher 1.3.0

The optional client mixin uses Forge's indexed mod lookup and retains Derender's fallback if Forge is unavailable. Shader state still comes from the active pipeline. The comparison used the same hidden, muted Minecraft 1.20.1 client, Forge 47.4.10, Microsoft Java 17.0.15, G1, 2–12 GiB heap, and 60 FPS cap. Lookup measurements used five warmup batches and twenty measured batches of 20,000 calls each.

| Run | Lookup median / p95 ns per call | Lookup median / p95 bytes per call | JFR stacks through the reflective fallback, lookup / render | Render frame median / p95 ms |
| --- | ---: | ---: | ---: | ---: |
| Baseline 1 | 555.85 / 1,415.46 | 368 / 368 | 18 / 5 | 17.94 / 30.53 |
| Baseline 2 | 1,009.56 / 1,770.10 | 368 / 368 | 17 / 4 | 17.16 / 23.00 |
| Patched 1 | 11.68 / 67.46 | 0 / 24 | 0 / 0 | 17.30 / 22.70 |
| Patched 2 | 6.96 / 199.39 | 0 / 24 | 0 / 0 | 17.54 / 22.92 |

Each run matched Forge's results on three installed mod IDs and two absent IDs. The patched runs averaged 6.20 and 2.92 bytes per call across all measured batches, with zero in the later batches after JIT warmup. Each run captured 28 scenes across BSL 10.1 off, on, off, and on. The Draconic pickaxe, sword, and bow rendered in first and third person. Both core renderers ran in every shader stage. The energy core's client-side animation state varied in one baseline run due to block-entity synchronization. The other baseline and both candidates showed the active core. Render frame times overlapped between the baseline and patched runs.

Use `tools/memory_client_smoke.py --mode memory-derender` with the same frozen pack input, smoke jar, and client JVM for both builds. Raw captures are local under `/tmp/auram-client-memory/cycle1-*`.

## Lucent 1.5.5 single-source lighting

The installed Lucent jar matched CurseForge file 4951434. The redirect matches both allocations in `addLightSource`. It adds the existing source to a fresh mutable list and calls Lucent's list constructor. The public jar and the installed jar had identical SHA-256 digests.

The focused comparison used the installed Lucent, ProjectE, and Patchouli jars in hidden Forge clients with Java 17, G1, 2–6 GiB heap, and the same smoke jar. Each run drove Lucent's real engine against a private client level. It used five warmup batches and twenty measured batches of 60 calls. Paired cases compared block positions, light levels, ordered source positions, and exact dirty-section sets. Both runs per build agreed across all eight tick checkpoints and four propagation scenes, including 1,099 positions with overlapping sources in the smooth scene. The route included an occluding wall, a block edit, and Overworld → Nether → Overworld travel.

| Run | Median / p95 KiB allocated per propagation call | Median / p95 ms per call | JFR single-source temporary arrays / wrappers / copy arrays |
| --- | ---: | ---: | ---: |
| Baseline 1 | 1,081.32 / 1,081.32 | 0.596 / 1.479 | 24 / 51 / 70 |
| Baseline 2 | 1,081.32 / 1,081.32 | 0.574 / 1.020 | 22 / 67 / 61 |
| Patched 1 | 932.96 / 932.96 | 0.541 / 5.005 | 0 / 0 / 0 |
| Patched 2 | 932.96 / 932.96 | 0.614 / 5.129 | 0 / 0 / 0 |

Allocation dropped 13.7%. JFR still observed the list and backing storage that hold the lights. Timing ran alongside other validation and varied across runs. Use `tools/memory_client_smoke.py --mode memory-lucent --xmx 6G` with the focused pack input. Raw captures are local under `/tmp/auram-client-memory/lucent-v4-*`.
