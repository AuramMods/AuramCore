package art.arcane.auram.vapor.client;

import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.VoxelOccupancy.SectionOccupancy;
import art.arcane.auram.vapor.VaporDemonstrators;
import art.arcane.auram.vapor.VaporNetwork;
import art.arcane.auram.vapor.VaporObstacles;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static art.arcane.auram.field.VoxelGrid.SECTION;
import static art.arcane.auram.field.VoxelGrid.SECTIONS;

/** Reads only block-resolution Voxy snapshots. The terrain cache does no I/O on the client thread. */
final class VoxyVaporSections {
    private static final Access ACCESS = Access.open();

    record Tile(SectionOccupancy occupancy, Map<Key, VaporNetwork.Emitter> emitters) {}

    private VoxyVaporSections() {}

    static Object world() {
        if (ACCESS == null) return null;
        try {
            Object renderer = ACCESS.renderer.invoke(null);
            return renderer == null ? null : ACCESS.engine.invoke(renderer);
        } catch (ReflectiveOperationException | LinkageError error) {
            return null;
        }
    }

    static CompletableFuture<Map<Position, Tile>> read(Object world, List<Position> parents) {
        return CompletableFuture.supplyAsync(() -> {
            var result = new HashMap<Position, Tile>();
            for (Position parent : parents) {
                try {
                    result.putAll(ACCESS.read(world, parent));
                } catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof IllegalStateException) break;
                    throw new IllegalStateException("Could not read Voxy terrain for vapor", failure.getCause());
                } catch (Throwable failure) {
                    if (failure instanceof Error error) throw error;
                    throw new IllegalStateException("Could not read Voxy terrain for vapor", failure);
                }
            }
            return result;
        }, Util.backgroundExecutor());
    }

    private record Access(Method renderer, Method engine, Method acquire, Method release,
                          Method data, Method coverage, Method known,
                          Method mapper, Method state, MethodHandle blockId) {
        static Access open() {
            try {
                Class<?> getter = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
                Method renderer = getter.getMethod("getNullable");
                Method engine = renderer.getReturnType().getMethod("getEngine");
                Class<?> world = engine.getReturnType();
                Method acquire = world.getMethod("acquireIfExists", int.class, int.class, int.class, int.class);
                Class<?> section = acquire.getReturnType();
                Method coverage = section.getMethod("getSurfaceData");
                Method mapper = world.getMethod("getMapper");
                Class<?> map = mapper.getReturnType();
                return new Access(renderer, engine, acquire, section.getMethod("release"),
                        section.getMethod("copyData"), coverage, coverage.getReturnType().getMethod("isKnownVoxel", int.class),
                        mapper, map.getMethod("getBlockStateFromBlockId", int.class),
                        MethodHandles.publicLookup().unreflect(map.getMethod("getBlockId", long.class)));
            } catch (ClassNotFoundException missing) {
                return null;
            } catch (ReflectiveOperationException | LinkageError error) {
                LogUtils.getLogger().debug("Voxy block-resolution vapor data is unavailable", error);
                return null;
            }
        }

        Map<Position, Tile> read(Object world, Position parent) throws Throwable {
            Object section = acquire.invoke(world, 0, parent.x(), parent.y(), parent.z());
            if (section == null) return Map.of();
            try {
                Object coverageData;
                long[] blocks;
                synchronized (section) {
                    coverageData = coverage.invoke(section);
                    blocks = (long[]) data.invoke(section);
                }
                Object mapping = mapper.invoke(world);
                var states = new Int2ObjectOpenHashMap<BlockState>();
                var result = new HashMap<Position, Tile>();
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        for (int x = 0; x < 2; x++) {
                            int base = index(x * SECTION, y * SECTION, z * SECTION);
                            if (!(boolean) known.invoke(coverageData, base)) continue;
                            Position tile = new Position(parent.x() * 2 + x, parent.y() * 2 + y, parent.z() * 2 + z);
                            result.put(tile, readTile(tile, blocks, base, mapping, states));
                        }
                    }
                }
                return result;
            } finally {
                release.invoke(section);
            }
        }

        private Tile readTile(Position tile, long[] blocks, int base, Object mapping,
                              Int2ObjectOpenHashMap<BlockState> states) throws Throwable {
            var occupied = new BitSet(SECTIONS.cells());
            var emitters = new HashMap<Key, VaporNetwork.Emitter>();
            var cursor = new BlockPos.MutableBlockPos();
            for (int z = 0; z < SECTION; z++) {
                for (int y = 0; y < SECTION; y++) {
                    for (int x = 0; x < SECTION; x++) {
                        int id = (int) blockId.invokeExact(blocks[base + index(x, y, z)]);
                        if (id == 0) continue;
                        BlockState block = states.get(id);
                        if (block == null) {
                            block = (BlockState) state.invoke(mapping, id);
                            states.put(id, block);
                        }
                        cursor.set(tile.x() * SECTION + x, tile.y() * SECTION + y, tile.z() * SECTION + z);
                        try {
                            if (VaporObstacles.blocksVapor(EmptyBlockGetter.INSTANCE, cursor, block)) {
                                occupied.set(SECTIONS.index(x, y, z));
                            }
                        } catch (RuntimeException needsLiveWorld) {
                            occupied.set(SECTIONS.index(x, y, z));
                        }
                        var emitter = VaporDemonstrators.lodEmitter(cursor, block);
                        if (emitter != null) emitters.put(new Key(emitter.id(), cursor), emitter);
                    }
                }
            }
            return new Tile(new SectionOccupancy(occupied), Map.copyOf(emitters));
        }

        private static int index(int x, int y, int z) {
            return (y << 10) | (z << 5) | x;
        }
    }
}
