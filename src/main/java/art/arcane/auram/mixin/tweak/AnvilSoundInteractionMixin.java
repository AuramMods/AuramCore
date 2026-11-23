package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.AnvilSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class AnvilSoundInteractionMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void auram$rememberPosition(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                        CallbackInfoReturnable<InteractionResult> result) {
        var level = Minecraft.getInstance().level;
        if (level != null && result.getReturnValue().consumesAction()
                && level.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL)) {
            AnvilSounds.requestOpen(level, hit.getBlockPos());
        }
    }
}
