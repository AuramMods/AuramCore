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
    private void auram$reserveJournalHeader(CallbackInfoReturnable<Boolean> result) {
        if (AuramMenu.isJournalGui(this)) {
            BaseScreen journal = (BaseScreen) (Object) this;
            journal.setHeight(Math.max(1, journal.height - AuramMenu.BAR_HEIGHT));
        }
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private void auram$startJournalBelowHeader(CallbackInfoReturnable<Integer> result) {
        if (AuramMenu.isJournalGui(this)) {
            result.setReturnValue(AuramMenu.BAR_HEIGHT);
        }
    }
}
