# Forge tweak validation

The fixtures used Minecraft 1.20.1, Forge 47.4.10, and Java 17. Each feature has an independent configuration under `config/auram/`. The Core defaults are off and the pack enables all four.

## Faster Random

The installed Faster Random 5.1.0 generated the reference vectors. All 45 vectors matched on Auram's full dedicated-server fixture and on stock Forge. They included mixed and bounded draws, reseeding, Gaussian draws, direct bit draws, forks, and slime seeds at negative and large coordinates. The replacement also passed 8,192 concurrent draws from the shared factory against its sequential reference.

Prism can expose duplicate Java 17 random providers. Core looks for a loader with an unambiguous provider list and preserves the vanilla fallback when none is available. The stock Prism client exercised that fallback. Both Microsoft and Temurin Java 17 exercised LXM on stock dedicated Forge.

Four full-pack runs continued the same seed and copied save and generated six new FULL chunks each. Canonical block-state comparisons found 2,122 changed positions between two runs of the original pack. The two replacement runs differed from the first original run at 7,677 and 2,513 positions, respectively. The original runs measured 0.066 and 0.029 FULL chunks per second. The replacement runs measured 0.042 and 0.245. Each JVM reported four CPUs. Decoration varied even among controls, and these short runs could neither establish block-for-block parity nor isolate a throughput gain.

## FastAnim

The [target inventory](../src/smokeTest/resources/auram-fastanim-targets.json) covers all 65 entries declared by the installed FastAnim 1.6. The stock Forge fixture found 91 merged methods, including helpers, over the 64 ported entries. Vanilla already uses the identical linear interpolation for the remaining magma-cube hook. The shipped FastAnim mixin list did not register its ModelUtil helper.

Connector logged an invalid descriptor for one of the original parrot injections. The port uses Forge's concrete state-based methods for both parrot hooks. The smoke test ran 18 successive poses across 12 vanilla models and compared 10,800 position and rotation components. The 20 differing components belonged to the parrot and had a maximum absolute difference of 0.000028. The probe measures pose compatibility.

## Anvils Resounded and Skip Transitions

The muted stock Forge client verified the actual book, repair, and rename output paths, plus invalid and abandoned results. It checked the sound IDs before the mixer and verified that a nearby anvil-use event kept its vanilla sound. The port uses the client menu, world, and exact anvil position to scope the substitution.

The transition probes checked the title fade timer, loading callback and failure propagation, toast position, and toast sounds before the mixer. The integrated stock client also selected and reloaded a real resource pack and verified that its resource reached the active resource manager. Separate enabled and disabled fixtures exercised the original four settings.
