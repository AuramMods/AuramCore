package art.arcane.auram.mixin.lucent;

import com.legacy.lucent.core.dynamic_lighting.LightData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Pseudo
@Mixin(value = LightData.class, remap = false)
public interface LightDataFactory {
    @Invoker("<init>")
    static LightData auram$create(int level, List<LightData.SourcePos> sources) {
        throw new AssertionError("Mixin did not supply the Lucent constructor");
    }
}
