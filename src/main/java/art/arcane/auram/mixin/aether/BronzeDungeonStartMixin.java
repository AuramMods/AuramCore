package art.arcane.auram.mixin.aether;

import art.arcane.auram.util.BronzeDungeonTerrain;
import art.arcane.auram.util.TerrainBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.aetherteam.aether.world.structure.BronzeDungeonStructure", remap = false)
public abstract class BronzeDungeonStartMixin {
    @Unique private static final ResourceLocation AURAM_BOSS_ROOM =
            ResourceLocation.fromNamespaceAndPath("aether", "bronze_dungeon/boss_room");

    @WrapMethod(method = "findStartingHeight")
    private static int auram$sampleStartingHeight(ChunkGenerator generator, LevelHeightAccessor level,
                                                   ChunkPos pos, RandomState random, StructureTemplateManager templates,
                                                   int aboveBottom, int belowTop, Operation<Integer> original) {
        if (!BronzeDungeonTerrain.supports(generator, level)) {
            return original.call(generator, level, pos, random, templates, aboveBottom, belowTop);
        }
        int roomHeight = templates.getOrCreate(AURAM_BOSS_ROOM).getSize().getY();
        TerrainBatch terrain = TerrainBatch.current(generator, level, random);
        if (terrain != null) {
            return BronzeDungeonTerrain.startingHeight(terrain, pos, level, roomHeight, aboveBottom, belowTop);
        }
        return TerrainBatch.scoped(generator, level, random, false, () -> BronzeDungeonTerrain.startingHeight(
                TerrainBatch.current(generator, level, random), pos, level, roomHeight, aboveBottom, belowTop));
    }

    @WrapMethod(method = "searchNearbyChunks")
    private static ChunkPos auram$reuseNeighborTerrain(ChunkPos pos, MutableInt height, ChunkGenerator generator,
                                                        LevelHeightAccessor level, RandomState random,
                                                        StructureTemplateManager templates, int aboveBottom, int belowTop,
                                                        Operation<ChunkPos> original) {
        if (!BronzeDungeonTerrain.supports(generator, level) || TerrainBatch.current(generator, level, random) != null) {
            return original.call(pos, height, generator, level, random, templates, aboveBottom, belowTop);
        }
        return TerrainBatch.scoped(generator, level, random, false,
                () -> original.call(pos, height, generator, level, random, templates, aboveBottom, belowTop));
    }
}
