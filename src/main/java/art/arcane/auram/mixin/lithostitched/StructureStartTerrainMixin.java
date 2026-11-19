package art.arcane.auram.mixin.lithostitched;

import art.arcane.auram.util.TerrainBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class StructureStartTerrainMixin {
    @WrapMethod(method = "createStructures")
    private void auram$shareStartTerrain(RegistryAccess registry, ChunkGeneratorStructureState state,
                                         StructureManager structures, ChunkAccess chunk,
                                         StructureTemplateManager templates, Operation<Void> original) {
        TerrainBatch.scoped((ChunkGenerator) (Object) this, chunk, state.randomState(), false, () -> {
            original.call(registry, state, structures, chunk, templates);
            return null;
        });
    }
}
