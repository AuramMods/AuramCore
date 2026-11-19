package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.FtbSurfaceSmokeClient;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen", remap = false)
abstract class FtbSurfaceClaimShiftMixin {
    @ModifyExpressionValue(method = "mouseReleased", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/gui/ChunkScreen;isShiftKeyDown()Z"))
    private boolean auram$testShift(boolean shift) {
        return shift || FtbSurfaceSmokeClient.shift;
    }
}
