package art.arcane.auram.mixin.terramity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.mcreator.terramity.procedures.FlintlockPistolRightclickedProcedure", remap = false)
public abstract class FlintlockReloadMixin {
    // The flintlock checks the offhand inside its reload branch.
    @Redirect(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;",
            remap = true))
    private static ItemStack auram$allowFlintlockReload(LivingEntity shooter) {
        return ItemStack.EMPTY;
    }
}
