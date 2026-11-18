package art.arcane.auram.smoke.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Mixin(Window.class)
public abstract class HiddenWindowMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", remap = false, target =
            "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"))
    private long auram$keepSmokeClientHidden(IntSupplier width, IntSupplier height,
                                                    Supplier<String> title, LongSupplier monitor) {
        if (Boolean.getBoolean("auram.smoke.hiddenWindow")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        }
        return ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
    }
}
