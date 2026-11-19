package art.arcane.auram.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** An owned copy of one chunk's surface. No live world or chunk escapes the capture. */
final class SurfaceSnapshot {
    private final int[] colors = new int[SurfaceTile.CHUNK_PIXELS];
    private final short[] heights = new short[SurfaceTile.CHUNK_PIXELS];

    static SurfaceSnapshot capture(ServerLevel level, ChunkAccess chunk) {
        SurfaceSnapshot snapshot = new SurfaceSnapshot();
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean ceiling = level.dimensionType().hasCeiling();
        int ceilingY = Math.min(chunk.getMaxBuildHeight(), chunk.getMinBuildHeight() + level.dimensionType().logicalHeight()) - 1;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                cursor.set(pos.getMinBlockX() + x, 0, pos.getMinBlockZ() + z);
                int y = ceiling ? ceilingSurface(chunk, cursor, ceilingY) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int index = z * 16 + x;
                int color = 0;
                // Skip thin, invisible blocks without searching far below the actual surface.
                for (int sample = y; sample >= Math.max(chunk.getMinBuildHeight(), y - 4); sample--) {
                    cursor.setY(sample);
                    color = chunk.getBlockState(cursor).getMapColor(chunk, cursor).id;
                    if (color != 0) {
                        break;
                    }
                }
                snapshot.heights[index] = (short) y;
                snapshot.colors[index] = color;
            }
        }
        return snapshot;
    }

    private static int ceilingSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int ceilingY) {
        int open = 0;
        for (int y = ceilingY; y >= chunk.getMinBuildHeight(); y--) {
            BlockState state = chunk.getBlockState(cursor.setY(y));
            if (state.isAir()) {
                open++;
            } else {
                if (open >= 2 && !state.is(Blocks.BEDROCK)) {
                    return y;
                }
                open = 0;
            }
        }
        return Math.max(chunk.getMinBuildHeight(), ceilingY - 8);
    }

    int[] render() {
        int[] pixels = new int[SurfaceTile.CHUNK_PIXELS];
        for (int i = 0; i < pixels.length; i++) {
            if (colors[i] == 0) {
                continue;
            }
            int delta = 0;
            if ((i & 15) != 0) {
                delta += heights[i] - heights[i - 1];
            }
            if (i >= 16) {
                delta += heights[i] - heights[i - 16];
            }
            var shade = delta > 0 ? MapColor.Brightness.HIGH
                    : delta < 0 ? MapColor.Brightness.LOW
                    : MapColor.Brightness.NORMAL;
            pixels[i] = MapColor.byId(colors[i]).calculateRGBColor(shade);
        }
        return pixels;
    }
}
