package art.arcane.auram.mixin.inventoryparticles;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractContainerMenu.class, priority = 1500)
abstract class ClickParticlesMixin {
    @Unique private static final String PARTICLES = "net.lopymine.ip.mixin.ScreenHandlerMixin";
    @Unique private static final String HANDLER = "spawnParticlesWhenPuttedInSlot";

    @TargetHandler(mixin = PARTICLES, name = HANDLER)
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void auram$findDisplayedSlot(int index, int button, ClickType type, Player player,
                                         CallbackInfo click, CallbackInfo particles,
                                         @Share("displayIndex") LocalIntRef displayIndex) {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
            particles.cancel();
            return;
        }
        var clickedMenu = (AbstractContainerMenu) (Object) this;
        int displayed = auram$displayIndex(clickedMenu, screen.getMenu(), index);
        if (displayed < 0) particles.cancel();
        else displayIndex.set(displayed);
    }

    @TargetHandler(mixin = PARTICLES, name = HANDLER)
    @ModifyArg(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"), index = 0, remap = false)
    private int auram$useDisplayedSlot(int index, @Share("displayIndex") LocalIntRef displayIndex) {
        return displayIndex.get();
    }

    @Unique
    private static int auram$displayIndex(AbstractContainerMenu clicked, AbstractContainerMenu displayed, int index) {
        if (index < 0 || index >= clicked.slots.size()) return -1;
        if (clicked == displayed) return index;

        // Creative displays wrappers that can be ordered differently from the backing menu.
        Slot target = clicked.slots.get(index);
        for (int i = 0; i < displayed.slots.size(); i++) {
            Slot slot = displayed.slots.get(i);
            if (!slot.isActive()) continue;
            if (slot == target || slot instanceof CreativeSlotWrapperAccess wrapper && wrapper.auram$backingSlot() == target) {
                return i;
            }
        }
        return -1;
    }
}
