package art.arcane.auram.mixin.waystones;

import art.arcane.auram.waystone.WaystoneDestinations;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.core.Waystone;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WaystoneBlockEntityBase.class, remap = false)
abstract class WaystoneMoveMixin {
    @Inject(method = "initializeFromExisting", at = @At("RETURN"))
    private void auram$waystoneMoved(ServerLevelAccessor level, Waystone stone, ItemStack stack, CallbackInfo ci) {
        if (WaystoneDestinations.isPlayerWaystone(stone)) WaystoneDestinations.changed();
    }
}
