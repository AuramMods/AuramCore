package art.arcane.auram.smoke.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.IntSupplier;

@Mixin(Window.class)
public abstract class HiddenWindowMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", remap = false, target =
            "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"),
            index = 0)
    private IntSupplier auram$keepSmokeClientHidden(IntSupplier width) {
        if (Boolean.getBoolean("auram.smoke.hiddenWindow")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        }
        return width;
    }
}
