# AuramCore

AuramCore is a Minecraft Forge mod for dynamically generated ore-rock gameplay, recipe chaos generation, and automated quest scaffolding.

## Version Compatibility

| Component | Value |
| --- | --- |
| Mod ID | `auram` |
| Mod Name | `Auram` |
| Mod Version | `1.0.0` |
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
./gradlew reobfJar
```

Windows:

```bat
gradlew.bat reobfJar
```

Primary output:

- `build/libs/auram-1.0.0.jar`

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
