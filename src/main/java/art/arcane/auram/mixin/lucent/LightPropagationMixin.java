package art.arcane.auram.mixin.lucent;

import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.legacy.lucent.core.dynamic_lighting.DynamicLightingEngine", remap = false)
public abstract class LightPropagationMixin {
    @Unique
    private static final Direction[] auram$DIRECTIONS = Direction.values();

    // The enhanced for loop only reads this array in enum declaration order.
    @Redirect(method = "addLights", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;", remap = false),
            require = 1, allow = 1, remap = false)
    private static Direction[] auram$propagationDirections() {
        return auram$DIRECTIONS;
    }
}
