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
