package art.arcane.auram.util;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.world.level.levelgen.NoiseSettings;

import java.util.concurrent.atomic.AtomicReferenceArray;

/** Bounded cache of exact preliminary heights for a single seed and generator shape. */
public final class PreliminarySurfaceCache {
    private static final int SIZE = 1 << 15;
    private final NoiseSettings settings;
    private final AtomicReferenceArray<Entry> slots = new AtomicReferenceArray<>(SIZE);

    public PreliminarySurfaceCache(NoiseSettings settings) {
        this.settings = settings;
    }

    public boolean matches(NoiseSettings settings) {
        return this.settings.equals(settings);
    }

    public Entry lookup(long column) {
        Entry entry = slots.get(index(column));
        return entry != null && entry.column() == column ? entry : null;
    }

    public void record(long column, int height) {
        int index = index(column);
        Entry existing = slots.get(index);
        if (existing == null || existing.column() != column) {
            slots.set(index, new Entry(column, height));
        }
    }

    private static int index(long column) {
        return (int) HashCommon.mix(column) & (SIZE - 1);
    }

    public record Entry(long column, int height) {}
}
