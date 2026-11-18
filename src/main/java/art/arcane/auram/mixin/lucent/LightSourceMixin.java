package art.arcane.auram.mixin.lucent;

import com.legacy.lucent.core.dynamic_lighting.LightData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

@Pseudo
@Mixin(targets = "com.legacy.lucent.core.dynamic_lighting.DynamicLightingEngine", remap = false)
public abstract class LightSourceMixin {
    @Redirect(method = "addLightSource", at = @At(value = "NEW", target =
            "(ILcom/legacy/lucent/core/dynamic_lighting/LightData$SourcePos;)Lcom/legacy/lucent/core/dynamic_lighting/LightData;"),
            require = 2, allow = 2, remap = false)
    private static LightData auram$singleLightSource(int level, LightData.SourcePos source) {
        var sources = new ArrayList<LightData.SourcePos>(1);
        sources.add(source);
        return LightDataFactory.auram$create(level, sources);
    }
}
