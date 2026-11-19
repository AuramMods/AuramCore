package art.arcane.auram.mixin.arsmagicalegacy;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.UUID;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "com.github.minecraftschurlimods.arsmagicalegacy.common.handler.TickHandler", remap = false)
public abstract class WaterAbilityMixin {
    @WrapOperation(method = "handleAbilities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;removeModifier(Ljava/util/UUID;)V", remap = true))
    private static void auram$rememberHealthReduction(AttributeInstance health, UUID id, Operation<Void> original,
                                                       @Share("healthReduction") LocalRef<AttributeModifier> previous) {
        previous.set(health.getModifier(id));
        original.call(health, id);
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "handleAbilities", slice = @Slice(from = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;removeModifier(Ljava/util/UUID;)V", remap = true)),
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/core/Registry;get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
                    ordinal = 1, remap = true))
    private static Object auram$captureWaterAbility(Registry<?> abilities, ResourceLocation id, Operation<Object> original,
                                                     @Share("waterAbility") LocalRef<Predicate<Player>> waterAbility) {
        Object ability = original.call(abilities, id);
        waterAbility.set((Predicate<Player>) ability);
        return ability;
    }

    @WrapOperation(method = "handleAbilities", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;isInWaterOrBubble()Z", remap = true))
    private static boolean auram$checkWaterWhenReady(Player player, Operation<Boolean> original,
                                                     @Share("waterAbility") LocalRef<Predicate<Player>> waterAbility,
                                                     @Share("healthReduction") LocalRef<AttributeModifier> previous,
                                                     @Share("waterTested") LocalBooleanRef tested) {
        if (!(player.level() instanceof ServerLevel level) || waterAbility.get() == null) return original.call(player);

        // AML's ability record reads an affinity depth and compares its bounds.
        // Check it once before touching the environment.
        if (!waterAbility.get().test(player)) return false;
        tested.set(true);

        var pos = player.blockPosition();
        if (player.isInWater() || level.isOutsideBuildHeight(pos) ||
                level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return original.call(player);
        }

        // The light branch has already failed. Keep the prior result during
        // an unknown bubble check, and let the next player tick retry.
        AttributeModifier old = previous.get();
        if (old != null) player.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(old);
        return false;
    }

    @WrapOperation(method = "handleAbilities", slice = @Slice(from = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;isInWaterOrBubble()Z", remap = true)),
            at = @At(value = "INVOKE", target =
                    "Lcom/github/minecraftschurlimods/arsmagicalegacy/api/affinity/Ability;test(Lnet/minecraft/world/entity/player/Player;)Z",
                    remap = false))
    private static boolean auram$reuseWaterEligibility(@Coerce Object ability, Player player, Operation<Boolean> original,
                                                        @Share("waterTested") LocalBooleanRef tested) {
        return tested.get() || original.call(ability, player);
    }
}
