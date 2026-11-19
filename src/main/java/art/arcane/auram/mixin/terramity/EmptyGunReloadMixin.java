package art.arcane.auram.mixin.terramity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {
        "net.mcreator.terramity.procedures.BlasphemicRaptureRightclickedProcedure",
        "net.mcreator.terramity.procedures.BlunderbussRightclickedProcedure",
        "net.mcreator.terramity.procedures.RocketLauncherRightclickedProcedure"
}, remap = false)
public abstract class EmptyGunReloadMixin {
    // These guns reload on use when empty and fire when loaded.
    @Redirect(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;",
            remap = true))
    private static ItemStack auram$allowEmptyReload(
            LivingEntity shooter, LevelAccessor level, double x, double y, double z, Entity entity, ItemStack gun) {
        return gun.getOrCreateTag().getDouble("ammoLoaded") <= 0
                ? ItemStack.EMPTY : shooter.getOffhandItem();
    }
}
