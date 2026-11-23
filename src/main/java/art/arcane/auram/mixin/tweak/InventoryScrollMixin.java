package art.arcane.auram.mixin.tweak;

import art.arcane.auram.client.scroll.HotbarScrollInput;
import art.arcane.auram.client.scroll.SmoothScrolling;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class InventoryScrollMixin implements HotbarScrollInput {
    @Shadow public int selected;
    @Unique private int auram$wraps;

    @Inject(method = "swapPaint", at = @At("HEAD"))
    private void auram$recordWheelWrap(double direction, CallbackInfo callback) {
        var settings = SmoothScrolling.settings();
        if (settings.hotbar() == 0 || !settings.rollover()) return;
        int next = selected - (int) Math.signum(direction);
        auram$wraps += Math.floorDiv(next, Inventory.getSelectionSize());
    }

    @Override
    public int auram$consumeWraps() {
        int wraps = auram$wraps;
        auram$wraps = 0;
        return wraps;
    }
}
