package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.LithoStartProbe;
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
public abstract class StructureStartScopeProbeMixin {
    @WrapMethod(method = "createStructures")
    private void auramSmoke$trackStructureStarts(RegistryAccess registry, ChunkGeneratorStructureState state,
                                                 StructureManager structures, ChunkAccess chunk,
                                                 StructureTemplateManager templates, Operation<Void> original) {
        var frame = LithoStartProbe.enter((ChunkGenerator) (Object) this, state.randomState(), chunk);
        try {
            original.call(registry, state, structures, chunk, templates);
        } finally {
            LithoStartProbe.leave(frame);
        }
    }
}
