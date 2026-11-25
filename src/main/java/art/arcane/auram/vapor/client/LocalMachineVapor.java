package art.arcane.auram.vapor.client;

import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.vapor.EnderIoVapor;
import art.arcane.auram.vapor.TinkersVapor;
import art.arcane.auram.vapor.VaporNetwork;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class LocalMachineVapor {
    private static final int MAX_INDEXED = 4096;
    private static final int MAX_SAMPLED = 2048;
    private static final Map<ClientLevel, Index> LEVELS = new IdentityHashMap<>();

    private LocalMachineVapor() {}

    static void add(ClientLevel level, BlockEntity entity) {
        var state = entity.getBlockState();
        String namespace = state.getBlock().builtInRegistryHolder().key().location().getNamespace();
        if (!EnderIoVapor.hasVisibleActivity(state)
                && !(namespace.equals("tconstruct") && TinkersVapor.isVisibleMachine(entity))) return;
        var index = LEVELS.computeIfAbsent(level, unused -> new Index());
        var pos = entity.getBlockPos();
        var column = index.chunks.computeIfAbsent(new ChunkPos(pos).toLong(), unused -> new LinkedHashMap<>());
        if (index.count < MAX_INDEXED || column.containsKey(pos.asLong())) {
            if (column.put(pos.asLong(), entity) == null) index.count++;
        }
        if (column.isEmpty()) index.chunks.remove(new ChunkPos(pos).toLong());
    }

    static void remove(ClientLevel level, BlockPos pos) {
        var index = LEVELS.get(level);
        if (index == null) return;
        long chunk = new ChunkPos(pos).toLong();
        var column = index.chunks.get(chunk);
        if (column == null) return;
        if (column.remove(pos.asLong()) != null) index.count--;
        if (column.isEmpty()) index.chunks.remove(chunk);
    }

    static void unload(ClientLevel level, ChunkPos chunk) {
        var index = LEVELS.get(level);
        if (index == null) return;
        var removed = index.chunks.remove(chunk.toLong());
        if (removed != null) index.count -= removed.size();
        if (index.count == 0) LEVELS.remove(level);
    }

    static void unload(ClientLevel level) {
        LEVELS.remove(level);
    }

    static Map<Key, VaporNetwork.Emitter> collect(ClientLevel level, VoxelGrid.Position origin) {
        var index = LEVELS.get(level);
        if (index == null) return Map.of();
        var result = new LinkedHashMap<Key, VaporNetwork.Emitter>();
        int x = origin.x() >> 4, z = origin.z() >> 4;
        int columns = VoxelGrid.WINDOW_BLOCKS / VoxelGrid.SECTION;
        for (int cx = x - 1; cx <= x + columns; cx++) {
            for (int cz = z - 1; cz <= z + columns; cz++) {
                var column = index.chunks.get(ChunkPos.asLong(cx, cz));
                if (column == null) continue;
                for (var entity : column.values()) {
                    if (entity.isRemoved()) continue;
                    if (EnderIoVapor.hasVisibleActivity(entity.getBlockState())) {
                        EnderIoVapor.collect(entity, origin, result);
                    } else {
                        TinkersVapor.collect(entity, origin, result);
                    }
                    if (result.size() >= MAX_SAMPLED) return result;
                }
            }
        }
        return result;
    }

    private static final class Index {
        private final Map<Long, Map<Long, BlockEntity>> chunks = new HashMap<>();
        private int count;
    }
}
