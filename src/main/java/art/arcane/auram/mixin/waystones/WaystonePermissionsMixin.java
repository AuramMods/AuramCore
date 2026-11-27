package art.arcane.auram.mixin.waystones;

import art.arcane.auram.waystone.WaystoneDestinations;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.blay09.mods.waystones.core.WaystoneEditPermissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerWaystoneManager.class, remap = false)
abstract class WaystonePermissionsMixin {
    @ModifyReturnValue(method = "mayEditWaystone", at = @At("RETURN"))
    private static WaystoneEditPermissions auram$allowOwnerToEditGlobal(WaystoneEditPermissions result,
                                                                        Player player, Level level, IWaystone stone) {
        if (result == WaystoneEditPermissions.GET_CREATIVE && WaystoneDestinations.isOwnedBy(stone, player)) {
            return WaystoneEditPermissions.ALLOW;
        }
        return result;
    }
}
