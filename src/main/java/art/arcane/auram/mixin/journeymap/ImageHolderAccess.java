package art.arcane.auram.mixin.journeymap;

import com.mojang.blaze3d.platform.NativeImage;
import journeymap.client.model.ImageHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ImageHolder.class, remap = false)
public interface ImageHolderAccess {
    @Invoker("partialImageUpdate")
    void auram$blit(NativeImage pixels, int x, int z);

    @Invoker("finishPartialImageUpdates")
    void auram$flushPixels();
}
