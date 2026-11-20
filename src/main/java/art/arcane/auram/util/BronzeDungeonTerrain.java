package art.arcane.auram.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public final class BronzeDungeonTerrain {
    private static final TagKey<Block> NON_SPAWNABLE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("aether", "non_bronze_dungeon_spawnable"));

    private BronzeDungeonTerrain() {}

    public static boolean supports(ChunkGenerator generator, LevelHeightAccessor height) {
        return generator.getClass() == NoiseBasedChunkGenerator.class
                && !((NoiseBasedChunkGenerator) generator).generatorSettings().value().isAquifersEnabled()
                && TerrainCell.supports((NoiseBasedChunkGenerator) generator, height);
    }

    public static boolean solid(TerrainBatch terrain, int x, int y, int z) {
        var block = terrain.state(x, y, z);
        return !block.isAir() && !block.is(NON_SPAWNABLE);
    }

    public static int startingHeight(TerrainBatch terrain, ChunkPos pos, LevelHeightAccessor level,
                                     int roomHeight, int aboveBottom, int belowTop) {
        int minX = pos.getMinBlockX() - 1;
        int minZ = pos.getMinBlockZ() - 1;
        int maxX = pos.getMaxBlockX() + 1;
        int maxZ = pos.getMaxBlockZ() + 1;
        int height = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - belowTop;
        int thickness = roomHeight + 2;
        int currentThickness = 0;

        for (int y = height + aboveBottom; y <= maxY; y++) {
            if (solid(terrain, minX, y, minZ) && solid(terrain, minX, y, maxZ)
                    && solid(terrain, maxX, y, minZ) && solid(terrain, maxX, y, maxZ)) {
                currentThickness++;
            } else {
                if (currentThickness > thickness) {
                    thickness = currentThickness;
                    height = y;
                }
                currentThickness = 0;
            }
        }

        // The original considers only runs terminated inside the search range.
        return height - (thickness + roomHeight) / 2;
    }
}
