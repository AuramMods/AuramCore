package art.arcane.auram.mixin.akashictome;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "vazkii.akashictome.client.HUDHandler", remap = false)
public abstract class HudPreviewMixin {
    @Unique private GuiGraphics auram$lastGraphics;

    @Inject(method = "onDrawScreen", at = @At("HEAD"), cancellable = true)
    private void auram$renderOncePerFrame(RenderGuiOverlayEvent.Post event, CallbackInfo ci) {
        // Forge delivers each overlay post with the same Graphics for this frame.
        GuiGraphics graphics = event.getGuiGraphics();
        if (graphics == auram$lastGraphics) ci.cancel();
        auram$lastGraphics = graphics;
    }

    @Redirect(method = "onDrawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;", remap = true))
    private ItemStack auram$readHeldTome(ItemStack held) {
        // The morph below receives a detached preview source.
        return held;
    }

    @WrapOperation(method = "onDrawScreen", at = @At(value = "INVOKE", target =
            "Lvazkii/akashictome/MorphingHandler;getShiftStackForMod(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack auram$previewOneGuide(ItemStack tome, String target, Operation<ItemStack> morph) {
        CompoundTag source = tome.getTag();
        if (source == null || target.equals("minecraft")) return tome;

        Tag guide = source.getCompound("akashictome:data").get(target);
        if (!(guide instanceof CompoundTag)) return tome;

        CompoundTag data = new CompoundTag();
        data.put(target, guide.copy());
        ItemStack scratch = new ItemStack(tome.getItem());
        CompoundTag preview = scratch.getOrCreateTag();
        preview.put("akashictome:data", data);
        Tag definedMod = source.get("akashictome:definedMod");
        if (definedMod != null) preview.put("akashictome:definedMod", definedMod.copy());

        ItemStack result = morph.call(scratch, target);
        return result == scratch ? tome : result;
    }
}
