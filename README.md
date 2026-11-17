# AuramCore

AuramCore is a Minecraft Forge mod for dynamically generated ore-rock gameplay, recipe chaos generation, and automated quest scaffolding.

## Version Compatibility

| Component | Value |
| --- | --- |
| Mod ID | `auram` |
| Mod Name | `Auram` |
| Mod Version | `1.0.4` |
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
- All original random draws still occur. Higher candidates run the complete vanilla search and piece callback.
- The production-mapped `fossil` suite compares 960 fixed-seed and boundary cases against the original algorithm, including accepted positions and RNG state. Vanilla starts at the sea-level boundary, so this is a small optimization rather than a major solution to fossil generation cost.

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

- `build/libs/auram-1.0.4-all.jar` (reobfuscated, includes MixinSquared; release as `Auram.jar`)

The plain `jar`/`reobfJar` output does not include the bundled dependency. Use the `-all.jar` for installation and releases. `deployToModpack` uses this bundled artifact.

### Headless Chunk Patch Smoke Test

`./gradlew reobfSmokeTestJar` builds a separate test mod at `build/libs/auram-1.0.4-smoke-test.jar`. It is never included in the release jar.

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

Copied snapshots produced zero identity hits in 7,680 provider comparisons. Reuse fixtures did hit, and the shortcut passed mutable capability/NBT/quantity checks, but the tested release-jar candidate did not improve the local update timings (see `validation/performance.json`). It is therefore excluded from the release. A live inventory hit-rate trace and a representative capability-heavy A/B benchmark are still needed before revisiting it. No persistent equality cache was added.

### Terrain Query Investigation

`--mode terrain --with-mod ae2 --with-mod malum --with-mod nyctophobia --with-mod lithostitched --with-mod modernfix --with-mod tectonic` records query reuse and structure-piece/RNG fingerprints across 12 neighboring attempts per structure. `--baseline-jar` runs the identical fixture against a reference jar. The test mod skips spawn-region generation in this dry-run mode; production startup is unchanged.

`--mode heightcache --with-mod lithostitched --with-mod modernfix --with-mod tectonic` checks context keys, eviction and concurrent access. `--mode density` records direct column/height fingerprints, mutable-column isolation, parallel-query equivalence, and two generated NOISE chunks per vanilla dimension for comparison across jars. Use the same terrain mods for a Tectonic fixture.

`--mode mappingscope` checks visitor ownership, the 4,096-input bound, nested failures, completed visitor cleanup and concurrent mapping scopes.

### Remaining Performance Work

- Re-profile Nether -> home on the dedicated host. The regression test proves the stale support path is fixed; the original profile did not record the coordinates needed to attribute every live freeze conclusively.
- Tom Storage still needs live comparison identity rates and a capability-heavy A/B inventory fixture. Its equality candidate remains test-only because the measured copied snapshots had no identity hits and the candidate did not improve update timing.
- Broad coordinate/column caching needs multiworker hit-rate data, mutable-column cloning and lifecycle validation. AE2's scans contained no duplicates within an attempt. The scoped patches already avoid much of the graph construction cost without storing terrain results globally.
- Attribute the expensive datapack commands before rewriting NBT selectors. Cached generic entity NBT can become stale within a tick and can change mod capability serialization behavior.
- Revisit GC/heap or worker counts only with allocation ownership and comparable measurements on the four-CPU host. Execution samples alone do not identify those settings.

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
