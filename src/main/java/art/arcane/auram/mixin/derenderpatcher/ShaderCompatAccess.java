package art.arcane.auram.mixin.derenderpatcher;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.derenderpatcher.compat.ShaderCompat", remap = false)
public interface ShaderCompatAccess {
    @Invoker("shouldAllowUnknownShader")
    static boolean auram$shouldAllowUnknownShader(ShaderInstance shader) {
        throw new AssertionError();
    }
}
