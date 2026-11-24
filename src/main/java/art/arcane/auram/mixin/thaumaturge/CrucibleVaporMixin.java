package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.content.crucible.BlockEntityCrucible;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityCrucible.class, remap = false)
abstract class CrucibleVaporMixin {
    private static final String PROCESS_ITEM = "attemptSmelt(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Consumer;Z)Lnet/minecraft/world/item/ItemStack;";

    @Shadow(remap = false) abstract boolean isBoiling();

    @Inject(method = "tick", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        if (isBoiling()) ThaumaturgeVapor.crucible((BlockEntityCrucible) (Object) this);
    }

    @Inject(method = PROCESS_ITEM, at = @At(value = "INVOKE", remap = false, target =
            "Lcom/leclowndu93150/thaumaturge/api/aspect/AspectList;entries()Ljava/util/List;"), require = 1)
    private void auram$dissolvePuff(CallbackInfoReturnable<ItemStack> callback, @Local AspectList accepted) {
        ThaumaturgeVapor.cruciblePuff((BlockEntityCrucible) (Object) this, accepted.totalAmount());
    }

    @Inject(method = PROCESS_ITEM, at = @At(value = "NEW", remap = false,
            target = "com/leclowndu93150/thaumaturge/api/crucible/CrucibleEvent$Crafted"), require = 1)
    private void auram$craftPuff(CallbackInfoReturnable<ItemStack> callback,
                                  @Local(argsOnly = true) ItemStack ingredient) {
        ThaumaturgeVapor.cruciblePuff((BlockEntityCrucible) (Object) this,
                AspectIndexAccess.index().of(ingredient).totalAmount());
    }
}
