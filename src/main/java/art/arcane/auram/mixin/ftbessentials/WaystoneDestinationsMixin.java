package art.arcane.auram.mixin.ftbessentials;

import art.arcane.auram.waystone.DestinationFeedback;
import art.arcane.auram.waystone.WaystoneDestinations;
import art.arcane.auram.waystone.WaystoneTeleportPos;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import dev.ftb.mods.ftbessentials.util.WarmupCooldownTeleporter;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
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

    @ModifyExpressionValue(method = "teleportTo", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object auram$findLinkedWaystone(Object destination, @Local(argsOnly = true) String name) {
        if (destination != null) return destination;
        var manager = (SavedTeleportManager) (Object) this;
        String key = WaystoneDestinations.key(name);
        var stone = WaystoneDestinations.forManager(manager).get(key);
        return stone == null ? null : new WaystoneTeleportPos(manager, key, stone);
    }

    @WrapOperation(method = "teleportTo", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbessentials/util/WarmupCooldownTeleporter;teleport(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Function;)Ldev/ftb/mods/ftbessentials/util/TeleportPos$TeleportResult;"))
    private TeleportPos.TeleportResult auram$confirmArrival(WarmupCooldownTeleporter teleporter, ServerPlayer player,
                                                         Function<ServerPlayer, TeleportPos> position,
                                                         Operation<TeleportPos.TeleportResult> original,
                                                         @Local(argsOnly = true) String name) {
        boolean warp = (Object) this instanceof SavedTeleportManager.WarpManager;
        Function<ServerPlayer, TeleportPos> feedback = traveler ->
                DestinationFeedback.withTravelFeedback(position.apply(traveler), name, warp);
        return original.call(teleporter, player, feedback);
    }
}
