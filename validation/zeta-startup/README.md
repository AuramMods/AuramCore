# Zeta startup map correction

The failed client log showed Quark and Supplementaries throwing
`ArrayIndexOutOfBoundsException` in fastutil rehash from
`ForgeZetaEventBus.subscribeMethod`. Forge then aborted construction, skipped
resource/client events and omitted `mod_resources`; missing textures followed.
The installed Zeta 1.0-31 bus used an unsynchronized `Object2ObjectOpenHashMap`.
This matches [Zeta's reported map race](https://github.com/VazkiiMods/Zeta/pull/80).

Auram wraps each bus's existing `convertedHandlers` map with
`Collections.synchronizedMap` on constructor return. Original map semantics,
including null entries, are preserved; no new lock spans Forge callbacks. The
mixin is optional when Zeta is absent and does not disable mods or lower loading
parallelism.

## Evidence

- `negative-control.json`: shipped AuramCore 1.0.8 reproduces the same map-resize
  corruption under concurrent writes to the actual installed Zeta bus map.
- `handler-map.json`: patched production-mapped bus passes six rounds with eight
  workers, 32,000 handlers per round (192,000 insertions and removals), identity
  checks, null contracts and mapped listener delivery/unsubscription on an
  isolated Forge bus. Test listeners are generated with the actual Zeta event
  descriptor; they are not bundled in the production jar.
- `client-startups.json`: three fresh full-pack client processes in one private
  Prism fixture load 630 mods. Each verifies successful Quark/Supplementaries
  construction, the synchronized live map, the Forge mod resource pack, decoded
  Ice and Fire/Distant Horizons/Quark/Supplementaries textures and stitched
  Quark/Auram sprites, then repeats after a resource reload. All three logs have
  zero construction failures, broken-state event refusals or resource-recovery
  fallbacks. The title-menu screenshot was inspected as well.
- `settings-restoration.json`: restored the nine resource-pack selections and
  362 missing bindings from the last committed options, preserving current
  values for other settings. The restored file exactly matches that baseline;
  other dirty runtime configuration files are outside this change.

Raw logs/screenshots stay in the ignored local `.qa` session. No live worlds or
server connections are used, and the real pack's configuration is not modified
by the startup fixture. The fixture uses a hidden window, disables the early
loading window only in its private config, and exits itself at the menu.

## Reproduction

Build the production and separate test jars:

```sh
./gradlew test reobfJarJar reobfSmokeTestJar
```

Run the actual bus test using Java 17 and an installed Forge 47.4.10 libraries
folder:

```sh
python3 tools/performance_smoke.py --java /path/to/java17 \
  --libraries /path/to/server/libraries --mode zetamap \
  --with-mod quark --with-mod supplementaries --no-build
```

Add `--baseline-jar /path/to/AuramCore-1.0.8.jar` for the expected failing
negative control. The fixture uses daemon stress workers and bounded waits so
corrupted controls cannot leave a permanent test hang.

For the full client (macOS/Prism defaults; paths are overridable):

```sh
python3 tools/startup_client_smoke.py --runs 3 --output /tmp/auram-startup-results
```

The script prints its temporary instance path and stores JSON, per-run logs and
menu screenshots. `QA_EVT` records identify actual completion. It performs real
launches rather than relying on a filename/configuration heuristic.

## User check after deployment

1. Launch the normal Auram instance. The title/menu textures should render.
2. Confirm the expected resource packs and custom keybindings are present.
3. Enter your world and inspect Quark/Supplementaries blocks and normal items.
4. Reload resources with F3+T; textures should remain present.

If a later issue appears, retain `minecraft/logs/latest.log` and a screenshot of
the affected object. The automated checks validate startup/resources, not every
world/rendering combination or shader interaction.
