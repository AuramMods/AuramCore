package art.arcane.auram.mixin.ftbquests;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.item.LootCrateItem", remap = false)
public abstract class LootCrateSoundMixin {
    // FTB's override is named use in development and m_7203_ in production.
    // Keep the existing volume, broadcast, random draw and opening logic.
    @ModifyArg(method = {"use", "m_7203_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            ordinal = 0, remap = true), index = 4, remap = false)
    private SoundEvent auram$lootPickupSound(SoundEvent original) {
        return SoundEvents.ITEM_PICKUP;
    }

    @ModifyArg(method = {"use", "m_7203_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            ordinal = 0, remap = true), index = 7, remap = false)
    private float auram$lootPickupPitch(float original) {
        return 0.5F;
    }
}
