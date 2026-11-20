package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.WorldgenTargets;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "org.dreamtinker.dreamtinker.library.worldgen.ScatterReplaceOreFeature", remap = false)
public abstract class DreamtinkerFeatureProbeMixin {
    @WrapMethod(method = {"m_142674_", "place"})
    private boolean auramSmoke$measureFeature(FeaturePlaceContext<?> context, Operation<Boolean> original) {
        if (!WorldgenTargets.ENABLED) return original.call(context);
        long cpu = WorldgenTargets.cpu();
        boolean result = original.call(context);
        WorldgenTargets.record(WorldgenTargets.Target.DREAM_FEATURE, WorldgenTargets.cpu() - cpu, 0);
        return result;
    }
}
