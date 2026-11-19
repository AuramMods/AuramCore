package art.arcane.auram.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

import java.util.function.Function;

/** Biome corner offsets owned by one surface rule context. */
public final class SurfaceBiomeSampler implements Function<BlockPos, Holder<Biome>> {
    private static final double[] OFFSETS = makeOffsets();
    private final long seed;
    private final BiomeManager.NoiseBiomeSource source;
    private final int[] entries = new int[8 * 8 * 4 * 4];

    public SurfaceBiomeSampler(long seed, BiomeManager.NoiseBiomeSource source) {
        this.seed = seed;
        this.source = source;
    }

    @Override
    public Holder<Biome> apply(BlockPos pos) {
        int x = pos.getX() - 2;
        int y = pos.getY() - 2;
        int z = pos.getZ() - 2;
        int quartX = x >> 2;
        int quartY = y >> 2;
        int quartZ = z >> 2;
        double fracX = (x & 3) / 4.0D;
        double fracY = (y & 3) / 4.0D;
        double fracZ = (z & 3) / 4.0D;
        int winner = 0;
        double nearest = Double.POSITIVE_INFINITY;

        for (int corner = 0; corner < 8; corner++) {
            int upperX = (corner >> 2) & 1;
            int upperY = (corner >> 1) & 1;
            int upperZ = corner & 1;
            int jitter = jitter(quartX + upperX, quartY + upperY, quartZ + upperZ);
            double dx = fracX - upperX + OFFSETS[jitter & 1023];
            double dy = fracY - upperY + OFFSETS[(jitter >>> 10) & 1023];
            double dz = fracZ - upperZ + OFFSETS[jitter >>> 20];
            double distance = Mth.square(dz) + Mth.square(dy) + Mth.square(dx);
            if (nearest > distance) {
                nearest = distance;
                winner = corner;
            }
        }

        return source.getNoiseBiome(quartX + ((winner >> 2) & 1), quartY + ((winner >> 1) & 1), quartZ + (winner & 1));
    }

    private int jitter(int x, int y, int z) {
        // Adjacent corners occupy distinct slots. Full coordinates avoid
        // aliasing even if a mod queries outside the surface chunk.
        int slot = ((x & 7) << 7) | ((z & 7) << 4) | ((y & 3) << 2);
        int encoded = entries[slot + 3];
        if (encoded != 0 && entries[slot] == x && entries[slot + 1] == y && entries[slot + 2] == z) {
            return encoded - 1;
        }
        long state = LinearCongruentialGenerator.next(seed, x);
        state = LinearCongruentialGenerator.next(state, y);
        state = LinearCongruentialGenerator.next(state, z);
        state = LinearCongruentialGenerator.next(state, x);
        state = LinearCongruentialGenerator.next(state, y);
        state = LinearCongruentialGenerator.next(state, z);
        int rawX = (int) (state >> 24) & 1023;
        state = LinearCongruentialGenerator.next(state, seed);
        int rawY = (int) (state >> 24) & 1023;
        state = LinearCongruentialGenerator.next(state, seed);
        int rawZ = (int) (state >> 24) & 1023;
        int packed = rawX | (rawY << 10) | (rawZ << 20);
        entries[slot] = x;
        entries[slot + 1] = y;
        entries[slot + 2] = z;
        entries[slot + 3] = packed + 1;
        return packed;
    }

    private static double[] makeOffsets() {
        double[] offsets = new double[1024];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = (i / 1024.0D - 0.5D) * 0.9D;
        }
        return offsets;
    }
}
