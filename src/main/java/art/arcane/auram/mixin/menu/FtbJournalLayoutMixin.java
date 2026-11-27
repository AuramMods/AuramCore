package art.arcane.auram.mixin.menu;

import art.arcane.auram.client.menu.AuramMenu;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseScreen.class, remap = false)
abstract class FtbJournalLayoutMixin {
    @Inject(method = "setFullscreen", at = @At("RETURN"))
    private void auram$reserveJournalRail(CallbackInfoReturnable<Boolean> result) {
        if (AuramMenu.isJournalGui(this)) {
            BaseScreen journal = (BaseScreen) (Object) this;
            journal.setWidth(Math.max(1, journal.width - AuramMenu.RAIL_WIDTH));
        }
    }

    @Inject(method = "getX", at = @At("HEAD"), cancellable = true)
    private void auram$startJournalBesideRail(CallbackInfoReturnable<Integer> result) {
        if (AuramMenu.isJournalGui(this)) {
            result.setReturnValue(AuramMenu.RAIL_WIDTH);
        }
    }
}
