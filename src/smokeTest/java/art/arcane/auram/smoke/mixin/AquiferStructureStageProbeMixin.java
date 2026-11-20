package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.AquiferReuseProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkGenerator.class)
public abstract class AquiferStructureStageProbeMixin {
    @WrapMethod(method = "createStructures")
    private void auramSmoke$starts(RegistryAccess registry, ChunkGeneratorStructureState state,
                                   StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates,
                                   Operation<Void> original) {
        if (!AquiferReuseProbe.TIME_STAGES) {
            original.call(registry, state, structures, chunk, templates);
            return;
        }
        var scope = AquiferReuseProbe.enter(AquiferReuseProbe.Stage.STARTS, (ChunkGenerator) (Object) this, state.randomState());
        try {
            original.call(registry, state, structures, chunk, templates);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }

    @WrapMethod(method = "applyBiomeDecoration")
    private void auramSmoke$features(WorldGenLevel level, ChunkAccess chunk, StructureManager structures,
                                      Operation<Void> original) {
        if (!AquiferReuseProbe.TIME_STAGES) {
            original.call(level, chunk, structures);
            return;
        }
        var scope = AquiferReuseProbe.enter(AquiferReuseProbe.Stage.FEATURES, (ChunkGenerator) (Object) this, null);
        try {
            original.call(level, chunk, structures);
        } finally {
            AquiferReuseProbe.leave(scope);
        }
    }
}
