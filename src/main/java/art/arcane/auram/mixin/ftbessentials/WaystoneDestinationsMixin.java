package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.WaystoneDestinations;
import art.arcane.auram.waystone.WaystoneTeleportPos;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import dev.ftb.mods.ftbessentials.util.WarmupCooldownTeleporter;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(value = SavedTeleportManager.class, remap = false)
abstract class WaystoneDestinationsMixin {
    @ModifyReturnValue(method = "getNames", at = @At("RETURN"))
    private Set<String> auram$includeWaystoneNames(Set<String> manual) {
        var stones = WaystoneDestinations.forManager((SavedTeleportManager) (Object) this);
        if (stones.isEmpty()) return manual;
        Set<String> names = new HashSet<>(manual);
        names.addAll(stones.keySet());
        return names;
    }

    @ModifyReturnValue(method = "destinations", at = @At("RETURN"))
    private Stream<SavedTeleportManager.DestinationEntry> auram$includeWaystoneDestinations(
            Stream<SavedTeleportManager.DestinationEntry> manual) {
        var manager = (SavedTeleportManager) (Object) this;
        var stones = WaystoneDestinations.forManager(manager);
        if (stones.isEmpty()) return manual;
        return Stream.concat(manual, stones.entrySet().stream()
                .filter(entry -> !WaystoneDestinations.manual(manager).containsKey(entry.getKey()))
                .map(entry -> new SavedTeleportManager.DestinationEntry(entry.getKey(),
                        new TeleportPos(entry.getValue().getDimension(), entry.getValue().getPos()))));
    }

    @Inject(method = "teleportTo", at = @At("HEAD"), cancellable = true)
    private void auram$teleportToWaystone(String name, ServerPlayer player, WarmupCooldownTeleporter teleporter,
                                         CallbackInfoReturnable<TeleportPos.TeleportResult> cir) {
        var manager = (SavedTeleportManager) (Object) this;
        if (WaystoneDestinations.manual(manager).containsKey(name.toLowerCase())) return;
        String key = WaystoneDestinations.key(name);
        var stone = WaystoneDestinations.forManager(manager).get(key);
        if (stone != null) cir.setReturnValue(teleporter.teleport(player, ignored -> new WaystoneTeleportPos(manager, key, stone)));
    }
}
