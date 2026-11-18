# Chunk-providing follow-up validation

These production-mapped headless Forge 47.4.10 checks address the main-thread
request paths in `nwOlAPyCVI.sparkprofile`. They prove the stated behavior and
request avoidance, not a hosted-server throughput or freeze-duration speedup.
The separate smoke-test jar is never bundled in AuramCore.

## Cycle 1: Malum periodic well detection

`cycle1-malum.json` records the artifact hash, installed Malum/Lodestone jars,
JVM command and results. `WellProximitySmoke` compares 3,072 loaded queries
against Malum's unchanged shared lookup, including negative coordinates,
chunk boundaries, vertical edges, multiple wells, empty results and other block
entities. The real periodic handler also runs with blocking chunk requests
forbidden: impossible boundary chunks are excluded, absent/pending relevant
chunks preserve both prior proximity states, and the next scan sees a completed
neighbor. The rejection lookup and client path are left intact.

Run with `tools/performance_smoke.py --mode wellproximity --with-mod malum`
and the script's required Java 17/libraries arguments. Use `--no-build` after
`./gradlew reobfJarJar reobfSmokeTestJar`.

## Cycle 2: fluid readiness guard

`cycle2-fluids.json` records a production-jar candidate run, the shipped 1.0.7
reference and an expected failing negative control. `compare_fluids.py` found
12,096 identical loaded snapshots: 21 fluid selections (empty/water/lava and 18
Mekanism fluids), source/flowing states, ordinary/negative/chunk-edge positions,
three immersion heights, players/armor stands/boats/riders, predicate and normal
update paths, and Overworld/Nether/End. Snapshots include velocity, fluid heights,
water/lava flags, fire and fall distance. Fixtures reuse/reset a small entity
pool; allocating thousands of capability-heavy fake players exhausted the heap
in the initial discarded fixture.

The pending-FULL test verifies that the original ticket guard passes while its
chunk future is incomplete. Blocking reads are forbidden. The old jar triggers
that assertion, while the candidate preserves motion and defers fluid heights;
a completed neighbor then restores water detection and fall-distance reset.
The guard has no cached fluid state and changes only the server-side fluid-scan
call site. These runs also boot successfully without Malum installed.

Run modes `fluidsreference` (shipped reference via `--baseline-jar`) and `fluids`
(candidate), both with `--with-mod mekanism`; compare their runtime directories
with `python3 tools/compare_fluids.py REFERENCE CANDIDATE`. Running `fluids` with
the old jar is the expected failing negative control.

## Cycle 3: bounded biome command rewrites

`cycle3-biomes.json` records three independent reference JVMs and three candidate
JVMs, all with Java 17, four active CPUs and the same heap. The isolated control
jar differs from its candidate only in `mixins.auram.json`, with the two biome
mixins removed. The initial shipped-1.0.7 reference also matched the same 138
semantic cases. Reference and candidate results match for complete biome values,
command counts/feedback, dirty flags, unloaded/volume/height errors, and biome
refresh batches decoded through Minecraft's actual packet/palette code. The
fixture does not connect an interactive client.

Each JVM measures 25 batches of 400 commands after five warmup batches, with
identical mixed-biome starting palettes and result checksums. Partial-height
miss/toggle workloads have median speedups of 7.00x/6.84x across the JVM medians;
every partial candidate median is below every corresponding reference median.
Full-height timings overlap; no consistent gain is claimed there. These are
fixture-local command timings, not hosted-server TPS or chunk-generation gains.
Semantic-case resolver calls fall from 477,184 to 64,000; 80 cases do less work.

The command still invokes the original chunk rewrite, resolver, validation,
dirty marking and refresh code. A resolver-identity/thread-local scope only
limits its vertical loop. Checks cover negative Y, section edges, reversed
corners, replacement filters/tags, full height, unrelated resolver calls,
nested exceptions, normal/exception cleanup and worker isolation. Ordinary
world generation retains its original full section range.

Run `biomesreference` with the control/reference jar and `biomes` with the
candidate. Use `--java-arg=-XX:ActiveProcessorCount=4` for these fixtures. Compare
with `python3 tools/compare_biomes.py --reference RUNTIME --candidate RUNTIME`,
repeating each option for independent JVM runs.

## Final combined regression run

`combined-final.json` records the final production jar and all three suites in
one 21-jar fixture, including installed Malum/Lodestone, Mekanism, ModernFix,
Pehkui, Relics, Aether/Redux, Midnight, Lithostitched and their dependencies.
All checks pass, including 3,072 well comparisons, missing/pending chunks,
removed block-entity recreation and saved-tag promotion, 12,096 fluid states
identical to shipped 1.0.7, and 138 biome command cases. The pre-refinement well
helper fails the new removed-block-entity negative control as expected. The
final helper uses vanilla Level.getBlockEntity's IMMEDIATE creation policy and
build-height check while reading directly from the ready chunk.

The recorded final artifact hash matches the reobfuscated jar; its biome class
bytes match those measured in the repeated benchmark. The production jar has
no smoke-test classes. `./gradlew test reobfJarJar reobfSmokeTestJar` also passes.
This is a targeted compatibility fixture, not a full-pack or hosted-world run.
