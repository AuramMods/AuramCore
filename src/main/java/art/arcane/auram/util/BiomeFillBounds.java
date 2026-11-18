package art.arcane.auram.util;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A short-lived range for a single fillbiome resolver; ordinary generation is unbounded. */
public final class BiomeFillBounds {
    private static final ThreadLocal<Bounds> ACTIVE = new ThreadLocal<>();

    private BiomeFillBounds() { }

    public static void fill(ChunkAccess chunk, BiomeResolver resolver, Climate.Sampler sampler, int fromY, int toY) {
        Bounds previous = ACTIVE.get();
        // /fillbiome quantizes to multiples of four. That leaves section indices
        // unchanged, including for negative Y and reversed command corners.
        ACTIVE.set(new Bounds(resolver, SectionPos.blockToSectionCoord(Math.min(fromY, toY)),
                SectionPos.blockToSectionCoord(Math.max(fromY, toY)) + 1));
        try {
            chunk.fillBiomesFromNoise(resolver, sampler);
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static int minimum(BiomeResolver resolver, int original) {
        Bounds bounds = ACTIVE.get();
        return bounds != null && bounds.resolver == resolver ? Math.max(original, bounds.minSection) : original;
    }

    public static int maximum(BiomeResolver resolver, int original) {
        Bounds bounds = ACTIVE.get();
        return bounds != null && bounds.resolver == resolver ? Math.min(original, bounds.maxSection) : original;
    }

    private record Bounds(BiomeResolver resolver, int minSection, int maxSection) { }
}
