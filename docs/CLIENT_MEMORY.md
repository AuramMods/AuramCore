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

## Lucent 1.5.5 propagation directions

The Forge runtime bytecode consumes its single `Direction.values()` result with `arraylength` and `aaload` inside `addLights`. The replacement returns a private cached array in enum declaration order to that loop alone. Its elements and the propagation positions, lookups, and callbacks are unchanged.

The two direction-cache runs matched every lighting fingerprint, dirty-section set, and work checksum from the constructor-only control. Median and p95 allocation dropped from 932.96 KiB to 833.20 KiB per call, a 10.7% reduction. JFR sampled 60 direction arrays in each constructor-only run and zero in both direction-cache runs. The new runs measured 0.819 / 2.099 ms and 0.687 / 1.755 ms per call (median / p95). Timing varied while other fixtures ran. These runs support the allocation reduction. They leave the CPU effect uncertain.

## Forge ModelBakery staging map

Forge 47.4.10 uses `ModelBakery.topLevelModels` to select models for parent resolution and baking. The installed Forbidden Arcanus mixin inserts magnetized boot variants during construction. The installed Fabric Model Loading mixin inserts extra model IDs during the same reload. The staging-field references in the installed mod bytecode are those two integrations and ModernFix Dynamic Resources. During gameplay Mekanism builds transmitter content through Forge's public bakery and its model sources.

The client mixin replaces only the staging map at the return of `ModelManager.apply`, after Forge has dispatched its model event and installed the baked model cache. It uses the production mappings `f_119214_` and `m_247616_`. The bakery remains available to late callers. The cleanup checks the bakery's actual interfaces and skips the Dynamic Resources implementation.

Matched title-screen heap dumps used HotSpot's live-object dump and Eclipse Memory Analyzer dominators.

| Measurement | Baseline | Cleared staging map |
| --- | ---: | ---: |
| Staging entries | 1,072,788 | 0 |
| Bytes exclusively retained by staging map | 42,717,920 | 48 |
| Bytes retained by whole bakery | 587,948,504 | 545,230,632 |
| Baked registry entries | 1,072,789 | 1,072,789 |
| Missing-model entries | 1 | 1 |

The map accounted for **40.74 MiB** of the retained bakery. Its only incoming references were the bakery and the map's own key and value views. Both heap runs matched the full baked registry fingerprint and all 560 checked model variants. The raw heap dumps and analysis reports stay local under `/tmp/auram-client-memory/models-title-*`.

The smoke and production jars each carry their own refmap. Separate mapping files keep a test jar from shadowing the bundled production mappings during startup.

The in-world model runs completed their first rendered scene, two ordinary resource reloads, and an override pack being enabled and removed. Each stage matched the registry fingerprint and missing-model count. Mekanism produced eight identical late-baked quads at each stage. The patched client rendered the overridden diamond sword and restored the original model. The held-item regions of the baseline and patched screenshots were effectively pixel-identical. Fine-grained item quad UV hashes were sensitive to placement in Auram's 16,384-pixel atlas, so the harness quantizes sprite-relative UVs to 1/256 while retaining exact position values. The model captures are local under `/tmp/auram-client-memory/models-render-v2-*`.

The production-mapped combined jar also started and reloaded its resource packs without Lucent or Derender in a smaller client fixture. The dedicated Forge 47.4.10 instance loaded 477 mods and reached `Done` with the combined patch classes.
