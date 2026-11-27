package art.arcane.auram.mixin.waystones;

import art.arcane.auram.waystone.WaystoneDestinations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.client.gui.screen.WaystoneSettingsScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WaystoneSettingsScreen.class)
abstract class WaystoneSettingsMixin {
    @WrapOperation(method = "init", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/blay09/mods/waystones/core/PlayerWaystoneManager;mayEditGlobalWaystones(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean auram$showPlayerWaystoneGlobalToggle(Player player, Operation<Boolean> original,
                                                         @Local IWaystone stone) {
        // Waystones sends the origin but omits the owner UUID on this screen. The server authorizes the edit.
        return WaystoneDestinations.isPlayerWaystone(stone) || original.call(player);
    }
}
