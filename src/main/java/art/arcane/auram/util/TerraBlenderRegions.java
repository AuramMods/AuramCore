package art.arcane.auram.util;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.IdentityHashMap;
import java.util.function.IntSupplier;

public final class TerraBlenderRegions {
    private static final ThreadLocal<Fill> ACTIVE = new ThreadLocal<>();

    private TerraBlenderRegions() {}

    public static void duringFill(Runnable action) {
        Fill previous = ACTIVE.get();
        ACTIVE.set(new Fill());
        try {
            action.run();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static int region(Object area, int x, int z, IntSupplier original) {
        Fill fill = ACTIVE.get();
        if (fill == null || fill.resolving) return original.getAsInt();
        return fill.region(area, x, z, original);
    }

    private static final class Fill {
        // Each TerraBlender positional parameter list creates its own outer Area.
        private final IdentityHashMap<Object, Long2IntOpenHashMap> byArea = new IdentityHashMap<>();
        private boolean resolving;

        private int region(Object area, int x, int z, IntSupplier original) {
            Long2IntOpenHashMap values = byArea.computeIfAbsent(area, ignored -> new Long2IntOpenHashMap(16));
            long key = ChunkPos.asLong(x, z);
            int cached = values.get(key);
            if (cached != 0 || values.containsKey(key)) return cached;
            resolving = true;
            try {
                int value = original.getAsInt();
                values.put(key, value);
                return value;
            } finally {
                resolving = false;
            }
        }
    }
}
