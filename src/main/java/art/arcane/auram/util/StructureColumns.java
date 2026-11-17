package art.arcane.auram.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.function.Supplier;

/** Read-only columns owned by one Wroughtnaut search; never shared with a world or worker. */
public final class StructureColumns {
    private static final int LIMIT = 1024;
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private StructureColumns() { }

    public static <T> T search(ChunkGenerator generator, LevelHeightAccessor height, RandomState state, Supplier<T> action) {
        if (generator.getClass() != NoiseBasedChunkGenerator.class) return action.get();
        Frame previous = ACTIVE.get();
        Frame frame = new Frame(generator, height, state);
        ACTIVE.set(frame);
        try {
            return action.get();
        } finally {
            frame.columns.clear();
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static NoiseColumn column(ChunkGenerator generator, int x, int z, LevelHeightAccessor height, RandomState state) {
        Frame frame = ACTIVE.get();
        if (frame == null || frame.generator != generator || frame.height != height || frame.state != state) {
            return generator.getBaseColumn(x, z, height, state);
        }
        long key = ChunkPos.asLong(x, z);
        NoiseColumn cached = frame.columns.get(key);
        if (cached != null) return cached;
        NoiseColumn column = generator.getBaseColumn(x, z, height, state);
        if (column != null && frame.columns.size() < LIMIT) frame.columns.put(key, column);
        return column;
    }

    private static final class Frame {
        final ChunkGenerator generator;
        final LevelHeightAccessor height;
        final RandomState state;
        final Long2ObjectOpenHashMap<NoiseColumn> columns = new Long2ObjectOpenHashMap<>(128);
        Frame(ChunkGenerator generator, LevelHeightAccessor height, RandomState state) {
            this.generator = generator;
            this.height = height;
            this.state = state;
        }
    }
}
