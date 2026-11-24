package art.arcane.auram.mixin.forge;

import art.arcane.auram.vapor.FurnaceFuelUse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, remap = false)
abstract class FurnaceFuelUseMixin {
    @Inject(method = "getBurnTime", at = @At("RETURN"))
    private static void auram$resolvedFuel(ItemStack stack, RecipeType<?> recipe, CallbackInfoReturnable<Integer> result) {
        FurnaceFuelUse.resolved(result.getReturnValueI());
    }
}
