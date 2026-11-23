package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.AnvilSounds;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilSoundTakeMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void auram$trackClientTake(Player player, ItemStack output, CallbackInfo callback) {
        if (player.level().isClientSide) AnvilSounds.tookResult((AnvilMenu) (Object) this);
    }
}
