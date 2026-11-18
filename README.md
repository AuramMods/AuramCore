# AuramCore

AuramCore is a Minecraft Forge mod for dynamically generated ore-rock gameplay, recipe chaos generation, and automated quest scaffolding.

## Version Compatibility

| Component | Value |
| --- | --- |
| Mod ID | `auram` |
| Mod Name | `Auram` |
| Mod Version | `1.0.8` |
| Minecraft Version | `1.20.1` |
| Forge Version | `47.4.10` |
| Forge Loader Range | `[47,)` |
| Java Toolchain | `17` |
| Mappings | `official` `1.20.1` |
| Gradle Wrapper | `8.8` |

Source of truth: `gradle.properties` and `build.gradle`.

## What The Mod Does

### 1. Dynamic Ore Rock System

- Scans all registered ore blocks and generates matching `auram:*rock*` items at runtime.
- Replaces ore block loot with generated rock items through a global loot modifier.
- Uses higher rock counts for deepslate ores.
- Applies dynamic names/colors so generated rocks match their source ore.
- Nether/netherrack, End/end-stone, and deepslate ore variants use matching host-stone surfaces while retaining a separately tinted ore interior. Host selection uses whole words in the source block ID, then its particle texture path; material names such as `enderium` do not imply an End host.
- The client generates these surfaces from the existing rock silhouette and the active vanilla host textures during atlas loading. Resource reloads rebuild the surfaces and clear cached ore/component colors. Ore tint sampling excludes pixels close to the host texture, so pale quartz is not overwhelmed by red netherrack.

### 2. Rock Conversion and Caching

- On server start/data reload, Auram calculates ore -> drop mappings and stores them in:
  - `config/auram/rock_recipes.json`
- Exposes those mappings as a built-in datapack (`auram_generated_recipes`) so players can craft outputs from rocks.
- If this is the first run, the mod logs that `/reload` or a restart may be needed to activate generated recipes.

### 3. Ore Chisel Enchantment

- Adds the `Ore Chisel` enchantment (max level 5, incompatible with Silk Touch).
- Right-clicking valid ore blocks with an enchanted pickaxe can chip off rock items.
- Higher enchant levels reduce break chance and cooldown.

### 4. Rock Catalyst Item

- Rock Catalyst can absorb picked-up rock items from player inventory pickup events.
- Converts sets of 4 matching rocks into their mapped ore-drop outputs.
- Shift-right-click dumps buffered rock progress back to the player.

### 5. Chaos Recipe Generation

- Command: `/auram chaos gen <difficulty>`
- Loads world recipes, applies random modifications, exports results as datapack JSON, and reloads.
- Generated datapack path:
  - `<world>/datapacks/auram_chaos_pack`
- Current modifier types include:
  - EMC-equivalent ingredient swaps
  - Added ingredients
  - Materialized component substitutions (custom gears/plates)

### 6. Quest Generation

- Command: `/auram chaos gen quests`
- Generates FTB Quests SNBT chapters and reward tables from recipe graphs and mob-kill ladders.
- Writes under:
  - `config/ftbquests/quests`
- Can optionally use an OpenAI-compatible chat endpoint (for example Ollama) to produce quest titles/descriptions.

### 7. Supporting Items and Integrations

- Custom items include `Ender Jade`, `Journey Map`, `Bundled Sticks`, `Rock Catalyst`, `Gear`, and `Plate`.
- JEI subtype integration is included for NBT-based component variants.
- With Patchouli installed, the purple **Auram Field Guide** is available in the Auram creative tab and can be crafted shapelessly from a book, cobblestone, and purple dye. Its five sections cover rocks, chaos/quest generation, configuration, integration items, and automatic compatibility patches. The book ID is `auram:auram`; Patchouli remains optional.

### 8. Midnight Compatibility Fix

- An optional Mixin targets The Midnight 0.6.7's Bog Strider movement and fall handlers.
- Entities without the enchantment or its active speed modifier skip terrain checks.
- Bog Strider only examines chunks that are already loaded, preventing its block checks from synchronously generating chunks on the server thread.
- Existing speed modifiers are removed when the relevant chunks are unavailable; Midnight's normal behavior runs when they are loaded.
- The Mixin is skipped when Midnight is not installed.

### 9. Nonblocking Chunk Compatibility Fixes

- Aether Redux 2.0.19: guard the Highlands quicksoil handler before its supporting-block position getter can read an unavailable chunk. Loaded quicksoil behavior is unchanged.
- Relics 0.8.0.11: read already-loaded chunks in the fluid collision scan. Unavailable samples are treated as empty; loaded fluid collision calculations and events remain in Relics.
- Mekanism 10.4.16: mark available chunks dirty without a blocking fetch. If a FULL ticket exists but the chunk is still loading, retain the notification until chunk load or a later tick. Cancelled loads and unloaded levels discard pending entries without creating chunk tickets.
- These patches affect logical server operations only and are optional when the corresponding mod is absent.
- MixinSquared 0.3.8 is bundled in the release jar to target the Relics mixin handler without relying on its generated method name. No separate installation is required.

### 10. Vanilla Support Position Cache

- Server-side dimension changes and explicit `moveTo`/`absMoveTo` relocations invalidate supporting-block and feet-state caches.
- If the cached supporting chunk is unavailable, the support-position getter discards that contact and lets vanilla calculate the position from current coordinates.
- Normal movement retains valid contacts. No enchantment callbacks are cancelled; Soul Speed and Frost Walker continue through their original handlers.
- The separate movement smoke suite covers stale support, fences/slabs, real command teleports between Nether/End/Overworld, and a real vanilla Nether portal search/return.

### 11. Malum Weeping Well Flatness

- The optional Malum 1.6.7 mixin retains its exact air/ceiling predicate and scan order, returning once the original threshold must pass or cannot be reached.
- Normal radius 8 scans at most 256 columns and needs 128 matches. There is no change to placement criteria or worldgen RNG usage.
- The headless `well` suite compares 5,100 predicate patterns and 12 fixed-seed terrain cases, including original candidate heights, against the full-scan reference and checks RNG state.
- The recorded rejection-workload benchmark uses 49.6% fewer columns; its median check time drops from 402.5 ms to 198.3 ms. This measures the local predicate, not whole-pack generation speed. Accepting patterns are covered separately by the equivalence suite.

### 12. Nether Fossil Search

- Rejects sampled heights at or below sea level before synthesizing a noise column. The original search can only decrease that height and would reject it anyway.
- Higher candidates sample successive block states lazily, reusing the previous floor as the next above-block state. Unsupported generator contexts retain the original complete-column path.
- All original random draws, sturdy-face tests, positions and piece callbacks remain. The follow-up `fossil` suite compares 1,056 fixed-seed/boundary cases per run, including full piece/RNG fingerprints from actual structure attempts. See `validation/followup/cycle3-summary.json` for the mixed-search benchmark and its limits.

### 13. Lithostitched Assembly Height Reuse

- A bounded cache belongs to each alternate jigsaw assembly. It stores integer heights, including the complete generator/state/accessor identity, bounds, position and query type in the key.
- The cache releases with the assembly, retains at most 1,024 entries and serializes access only within that assembly. It never shares NoiseChunks, columns or RNG state between workers.
- Twelve fixed-seed village assemblies made 2,473 height queries instead of 3,344. Full serialized structure layouts and RNG fingerprints matched. Timing did not fall proportionally; see the recorded measurements rather than assuming a 26% wall-clock gain.

### 14. Shared Density Graph Mapping

- Variable-depth NoiseChunk routers memoize repeated transformations of shared vanilla density-function branches during each constructor mapping pass.
- The mapping visitor owns its identity cache, capped at 4,096 inputs. The cache clears on success or failure and is disabled afterward, including when a custom function retains the visitor. There is no world-global or worker-global cache.
- Each constructor still owns its mutable NoiseChunk state; separate queries return independent columns. Vanilla constant-depth Nether/End routers keep the original mapping pass.
- Independently booted reference/candidate servers matched column/height fingerprints, parallel queries, mutable-column isolation, and generated NOISE chunks across three seeds and all three vanilla dimensions, with both vanilla terrain and the Tectonic fixture. All 60 structure layout/RNG fingerprints also matched.

Local Tectonic fixture measurements (the reference already contains the early-out/support patches; the final candidate also contains assembly height reuse):

| Workload | Reference | Final candidate |
| --- | ---: | ---: |
| Median Overworld column | 20.78 ms | 4.15 ms |
| 12 meteorite attempts | 58.31 s | 9.53 s |
| 12 village attempts | 65.91 s | 10.13 s |
| 12 well attempts | 5.19 s | 1.27 s |
| 12 mushroom attempts | 0.42 s | 0.09 s |

These measure local workloads, not full-pack throughput or the hosted server. Detailed results, hashes, fixture versions and limitations are in `validation/performance.json`.

### 15. KubeJS Update Check

- KubeJS 2001.6.5-build.16 performs an unconditional startup update request without a configuration switch.
- An optional mixin cancels only that updater task before it creates an HTTP client. Script loading, lifecycle events, and reload handling still run normally.
- The mixin is skipped when KubeJS is absent. Other mods' update checks are disabled through the pack's Forge and individual mod configuration files.

### 16. Terrain Mapping Bookkeeping

- Vanilla density-node eligibility is classified once per class instead of comparing class names for each graph edge.
- Variable-depth NoiseChunks bypass structural interning for vanilla nodes that `wrapNew` leaves unchanged. Marker caches, holder unwrapping, beardifiers and blending still take the original wrapper path. Constant-depth Nether/End routers retain their original wrapper lookups.
- Both vanilla's `computeIfAbsent` and ModernFix's `get`/`put` implementation are supported without allocating a cancellable callback per node.
- `--mode mappingperf` runs eight warmup batches and 25 measured batches of 16 columns per dimension, fingerprints every resulting block state outside the timed interval, and checks mapping-scope bounds/lifetime/concurrency. Results for the four follow-up cycles are recorded in `validation/server3-cycles.json`.

### 17. Wroughtnaut Search Columns

- Mowzie's Wroughtnaut candidate and wall checks reuse read-only columns within a single `generatePieces` search. Coordinates use a full-width X/Z key; generator, height-accessor and random-state identity must match.
- Each search owns at most 1,024 columns, releases them in `finally`, and restores an enclosing search after nested calls/errors. Custom generator subclasses retain their original query path.
- `--mode wrought --with-mod mowziesmobs` exercises the installed mod's entry point and records query counts, pieces and RNG/output fingerprints. `--mode columnscope` tests bounds, cleanup, nested exceptions, uncached external calls and parallel search ownership.
- The initial 24-attempt Tectonic fixture removed only five repeated queries out of 862. This is a small avoided-work improvement; it did not establish a material wall-clock speedup. Nearby-column batching is a separate follow-up cycle.

### 18. Sparse Malum Terrain Checks

- Well flatness checks sample only the two block states they inspect. The cell sampler keeps the original complete height settings, aquifer/fluid picker, default block and empty-beardifier behavior; it skips constructing a full column array and evaluating every vertical block.
- Every sampler is owned and closed by its caller. Custom generator subclasses and unusual partial-cell height accessors retain the original generator path.
- `--mode sparse --with-mod modernfix --with-mod lithostitched --with-mod tectonic --with-mod bettercaves` compares shuffled-height, negative-coordinate and boundary queries with original full columns across three seeds/dimensions, including parallel ownership checks. The `well` benchmark compares the new sampler against the already-shipped early-out scan, rather than counting that earlier optimization a second time.

### 19. Nearby Structure Terrain Batches

- AE2 meteorites reuse dense height samples for neighboring columns in the same native noise cell. Every original requested height, statistics accumulation and random draw is retained.
- Malum checks share sparse two-state sampling between neighboring columns. Wroughtnaut searches share setup between their unique columns as well as caching exact duplicates. Lithostitched's assembly-local height cache reuses lazy cell setup between nearby queries.
- Each batch retains at most 16 cells. Invocation-owned batches close on success/failure; jigsaw batches belong only to their assembly cache, serialize access there, and close when generator/state/accessor context changes. World, accessor bounds and random-state contexts never share mutable interpolation state.
- `--mode terrainbatch` checks all heightmap types, complete column snapshots, negative coordinates, eviction, mutable snapshot isolation, closed/nested scopes and independent workers. It also runs the existing assembly-cache and column-scope checks. `--mode terrain` records real structure/RNG fingerprints and NoiseChunk construction counts for the installed structure mods.

### 20. Selected Held-Item NBT Selectors

- For a server player holding an item, test the live `SelectedItem` projection first. A mismatch or a predicate containing only that field avoids serializing the full player and its capabilities.
- Empty hands, non-player entities and successful predicates with additional root keys retain full serialization. There is no NBT cache.
- `--mode selectors` replays installed Guns/Scorched/Incendium functions and compares command traces, results and serialization counts. `tools/compare_selectors.py` repeats control/candidate runs. The 291 semantic checks include inversion, malformed types, custom serializers and same-stack mutations. See `validation/followup/cycle1-summary.json`.

### 21. Tom Storage Packet ID Reuse

- Tom Storage 1.7.1 already looks up an item's ID before writing it. Reuse that exact nonzero ID instead of performing the same capability-heavy map lookup a second time.
- New items, ID zero and custom map implementations retain the original path. Quantity encoding, flags, packet splitting and oversized-NBT fallback are preserved.
- `--mode terminal --with-mod toms_storage --with-mod mekanism` compares packet bytes and decoded client state with 128 powered Mekanism items, mutable supplemental capabilities and 60 transition cases. This removes approximately 25% of provider comparisons in that fixture; it does not cache equality across calls.

### 22. Nyctophobia Mushroom Threshold

- Mushroom placement needs only to know whether first **occupied** height exceeds 64. Query the threshold state first and scan higher states only if needed; do not construct a complete column or conflate occupied and free height.
- Floating terrain, all heightmap predicates, custom-generator fallback, closed contexts and independent workers are covered by `--mode structureprobes`. Actual piece/RNG fingerprints match the previous jar; see `validation/followup/cycle3-summary.json`.

### 23. FTB Quests Loot-Box Sound

- Right-click opening FTB Quests loot crates uses the item-pickup sound at a fixed pitch of `0.5`, including sneak-opening a stack.
- The optional FTB Quests 2001.4.17 mixin preserves its existing volume, sound category, reward handling, and crate consumption. Other item-break sounds are unaffected.
- `tools/performance_smoke.py --mode loot --with-mod ftbquests` (with the Java/libraries arguments described below) exercises the installed crate item on a disposable server and checks the actual sound event, pitch, volume, and consumption for normal/sneak opening plus an unrelated item-break control.

### 24. Malum Periodic Well Detection

- The periodic player proximity scan visits only relevant, immediately available chunks. Missing or pending chunks retain the previous proximity state until the next scan; the shared rejection/teleport lookup is unchanged.
- Loaded detection preserves Malum/Lodestone's search box and vanilla block-entity creation policy, including removed entries and pending saved tags.

### 25. Fluid Chunk Readiness

- The server-side fluid scan strengthens its existing unloaded-chunk guard with immediate FULL-chunk availability. A pending ticket defers the scan instead of blocking the server thread.
- Loaded fluid calculations remain unchanged. Validation compares 12,096 states across water, lava, 18 Mekanism fluids, boats and all three vanilla dimensions.

### 26. Bounded Biome Commands

- `/fillbiome` rewrites only vertical sections intersecting the command range. Original replacement predicates, results, dirty marking and biome refreshes are preserved; ordinary world generation remains unbounded.
- Resolver-local scopes restore after nested calls and exceptions. Validation matches 138 command cases and decoded refresh payloads; partial fills are about seven times faster in the isolated four-CPU fixtures. These timings are not whole-server or chunk-generation speedups.
- Reproduction instructions and detailed evidence for all three patches are in [validation/chunk-providing/README.md](validation/chunk-providing/README.md).

## Commands

All Auram commands require permission level 2 (`/op` or equivalent).

- `/auram chaos gen <difficulty>`
- `/auram chaos gen quests`
- `/auram chaos clear`

## Configuration

Forge common config class: `art.arcane.auram.AuramConfig`

Main settings include:

- `llmBaseUrl` (default `http://localhost:11434/v1/`)
- `llmModel` (default `gpt-oss-20b`)
- `llmKey` (default `ollama`)
- `questsUseLLM` (default `false`)
- `questThreads` (default `0`, auto)
- `defaultDifficulty` (default `50`)
- `varianceMultiplier` (default `1.9`)
- `debugMode` (default `false`)

## Build Instructions

### Prerequisites

- JDK 17 (toolchain target is Java 17)
- Internet access on first dependency resolution
- macOS/Linux shell or Windows CMD/PowerShell

### Build A Reobfuscated Mod Jar

macOS/Linux:

```bash
./gradlew reobfJarJar
```

Windows:

```bat
gradlew.bat reobfJarJar
```

Primary output:

- `build/libs/auram-1.0.8-all.jar` (reobfuscated, includes MixinSquared; install as `AuramCore-1.0.8.jar`)

The plain `jar`/`reobfJar` output does not include the bundled dependency. Use the `-all.jar` for installation and releases. `deployToModpack` uses this bundled artifact.

### Field Guide Validation

`./gradlew runClient -PbookClientSmoke` starts an invisible client in `build/book-client-smoke`, creates a disposable flat world, validates Patchouli content, the guide recipe and creative-tab entry, and renders every book spread to `guide-screenshots/`. It writes `auram-book-pass.txt` or `auram-book-failed.txt` in that runtime directory and exits. No live pack or existing save is used.

### Rock Appearance Validation

`./gradlew test` checks host detection, ore/background color separation, transparency, and differently sized source textures.

`./gradlew runClient -ProckClientSmoke` launches an isolated invisible client at the main menu, validates stitched host sprites, vanilla rock models and quartz tint, reloads resources, and repeats the checks before exiting. It writes `build/rock-client-smoke/auram-rock-client-pass.txt` on success (or `auram-rock-client-failed.txt` on failure) and exports the three generated host sprites beside it. It does not load a world or deploy to the pack.

### Headless Chunk Patch Smoke Test

`./gradlew reobfSmokeTestJar` builds a separate test mod at `build/libs/auram-1.0.8-smoke-test.jar`. It is never included in the release jar.

Run this test mod alongside the release jar, ProjectE, Aether Redux, Relics, Mekanism and their dependencies in a disposable Forge 47.4.10 dedicated-server directory with Java 17. Use flat terrain and the Highlands quicksoil setting. The test creates remote chunks and a Mekanism tank and stops the server itself.

Start that server twice in the same disposable directory:

1. The first process exercises all three unavailable-chunk hooks with an injector that throws if they call the blocking chunk-fetch API. It also checks Highlands quicksoil acceleration and the Relics fluid event beside an unavailable chunk, then saves a Mekanism tank containing 1234 mB of water.
2. The second process exercises the pending FULL-ticket dirty-marking path and verifies that the saved fluid survives a process restart.

Successful completion writes `auram-smoke-pass.txt`; failures are logged and write `auram-smoke-failed.txt`. Keep the test mod out of normal client/server installations.

`tools/performance_smoke.py` prepares a fresh disposable server, resolves dependencies from the installed modpack, builds the production-mapped jars, and records console logs, fixture hashes and results. For the movement suite:

```bash
python3 tools/performance_smoke.py --mode movement \
  --java /path/to/java17/bin/java --libraries /path/to/forge-server/libraries \
  --with-mod aether_redux --with-mod relics --with-mod mekanism --with-mod netherportalfix
```

Use `--baseline-jar /path/to/older/Auram.jar` as a regression control. The 1.0.2 control fails at the forbidden stale-support chunk fetch; the patched version passes. See `validation/performance.json` for recorded results. This runner never copies jars into the live instance.

Use `--mode well --with-mod malum` with the same Java/libraries arguments to run the terrain equivalence and paired A/B benchmark in a fresh normal-terrain world. Results are written to `well-benchmark.json` in the printed disposable runtime directory.

Use `--mode fossil --with-mod modernfix --with-mod lithostitched` for the Nether fossil equivalence suite.

### Performance Investigation: Item Equality

`--mode items --with-mod toms_storage --with-mod modernfix` runs the installed terminal sync implementation with copied snapshots, reused wrappers, and reused ItemStacks. Add `--identity-candidate` to enable an experimental shortcut in the **test mod only**.

Copied snapshots produced zero identity hits in 7,680 provider comparisons. Reuse fixtures did hit, and the shortcut passed mutable capability/NBT/quantity checks, but the tested release-jar candidate did not improve the local update timings (see `validation/performance.json`). It is therefore excluded from the release. A different production optimization now reuses the ID already looked up during the same packet write; it needs no equality shortcut or persistent cache. The capability-heavy packet suite validates this path with actual Mekanism items. See `validation/followup/cycle2-summary.json`.

### Terrain Query Investigation

`--mode terrain --with-mod ae2 --with-mod malum --with-mod nyctophobia --with-mod lithostitched --with-mod modernfix --with-mod tectonic` records query reuse and structure-piece/RNG fingerprints across 12 neighboring attempts per structure. `--baseline-jar` runs the identical fixture against a reference jar. The test mod skips spawn-region generation in this dry-run mode; production startup is unchanged.

`--mode heightcache --with-mod lithostitched --with-mod modernfix --with-mod tectonic` checks context keys, eviction and concurrent access. `--mode density` records direct column/height fingerprints, mutable-column isolation, parallel-query equivalence, and two generated NOISE chunks per vanilla dimension for comparison across jars. Use the same terrain mods for a Tectonic fixture.

`--mode mappingscope` checks visitor ownership, the 4,096-input bound, nested failures, completed visitor cleanup and concurrent mapping scopes.

### Remaining Performance Work

- Re-profile Nether -> home on the dedicated host. The regression test proves the stale support path is fixed; the original profile did not record the coordinates needed to attribute every live freeze conclusively.
- Tom Storage packet ID reuse now passes the capability-heavy paired fixture. A live factory benchmark would establish its impact on total server tick time; the separate identity-shortcut experiment remains excluded.
- Broad coordinate/column caching needs multiworker hit-rate data, mutable-column cloning and lifecycle validation. AE2's scans contained no duplicates within an attempt. The scoped patches already avoid much of the graph construction cost without storing terrain results globally.
- Installed function replay now identifies the held-item selector work in Guns. Inventory and successful multi-key predicates still serialize the full entity. Generic NBT caching remains unsafe because commands and capabilities can mutate within a tick.
- The four-CPU VM comparison is documented in `validation/followup/cycle4-tuning.md`. Repeat it on the actual host and retained save before adopting JVM changes; fresh-world measurements do not establish the existing factory's memory needs.

The config review found the relevant ModernFix, FerriteCore and Lithostitched optimizations already enabled. No modpack config, EMC value, live jar, launcher tag or release is changed by this performance goal.

### Full Build (Includes Auto Deploy Step)

```bash
./gradlew build
```

Important: `build` is finalized by `deployToModpack`, which copies the jar to `modpack_deploy` from `gradle.properties`.

Current default:

- `/Users/cyberpwn/Library/Application Support/PrismLauncher/instances/Auram/minecraft/mods/Auram.jar`

Override for your machine:

```bash
./gradlew build -Pmodpack_deploy="/path/to/your/minecraft/mods"
```

### Useful Dev Tasks

- `./gradlew genIntellijRuns`
- `./gradlew genEclipseRuns`
- `./gradlew runClient`
- `./gradlew runServer`
- `./gradlew runData`

## Runtime Notes

- This project contains direct ProjectE API usage for EMC-aware recipe logic.
- Quest generation is designed around FTB Quests file/layout conventions.
- JEI support is present for component subtype handling.

## License

- Declared mod license: `All Rights Reserved`
- See `LICENSE.txt` and `CREDITS.txt` for bundled legal/attribution files.
