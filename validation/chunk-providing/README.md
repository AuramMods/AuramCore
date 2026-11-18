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
