package art.arcane.auram.mixin.aether;

import art.arcane.auram.util.BronzeDungeonTerrain;
import art.arcane.auram.util.TerrainBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.aetherteam.aether.world.structurepiece.bronzedungeon.BronzeDungeonBuilder", remap = false)
public abstract class BronzeDungeonTerrainMixin {
    @Shadow @Final private Structure.GenerationContext context;

    @WrapMethod(method = "initializeDungeon")
    private void auram$shareRoomTerrain(BlockPos start, Structure.GenerationContext generationContext,
                                        StructurePiecesBuilder builder, Operation<Void> original) {
        if (!BronzeDungeonTerrain.supports(context.chunkGenerator(), context.heightAccessor())) {
            original.call(start, generationContext, builder);
            return;
        }
        TerrainBatch.scoped(context.chunkGenerator(), context.heightAccessor(), context.randomState(), false,
                () -> original.call(start, generationContext, builder));
    }

    @WrapMethod(method = "checkForAirAtPos")
    private boolean auram$sampleTunnelBlock(int x, int y, int z, Operation<Boolean> original) {
        TerrainBatch terrain = auram$roomTerrain();
        return terrain == null ? original.call(x, y, z) : terrain.state(x, y, z).isAir();
    }

    @WrapMethod(method = "isCoveredAtPos")
    private boolean auram$sampleRoomCorners(BoundingBox room, Operation<Boolean> original) {
        TerrainBatch terrain = auram$roomTerrain();
        if (terrain == null) return original.call(room);
        int minX = room.minX() - 1;
        int minZ = room.minZ() - 1;
        int maxX = room.maxX() + 1;
        int maxZ = room.maxZ() + 1;
        int minY = room.minY() - 1;
        int maxY = room.maxY() + 1;
        return auram$covered(terrain, minX, minZ, minY, maxY)
                && auram$covered(terrain, minX, maxZ, minY, maxY)
                && auram$covered(terrain, maxX, minZ, minY, maxY)
                && auram$covered(terrain, maxX, maxZ, minY, maxY);
    }

    @Unique
    private TerrainBatch auram$roomTerrain() {
        if (!BronzeDungeonTerrain.supports(context.chunkGenerator(), context.heightAccessor())) return null;
        return TerrainBatch.current(context.chunkGenerator(), context.heightAccessor(), context.randomState());
    }

    @Unique
    private static boolean auram$covered(TerrainBatch terrain, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            if (!BronzeDungeonTerrain.solid(terrain, x, y, z)) return false;
        }
        return true;
    }
}
