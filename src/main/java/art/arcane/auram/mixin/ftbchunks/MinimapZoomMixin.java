package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.MinimapZoom;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftblibrary.snbt.config.DoubleValue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FTBChunksClient.class, remap = false)
abstract class MinimapZoomMixin {
    @Shadow private double prevZoom;
    @Shadow private int minimapTextureId;
    @Shadow public abstract float getZoom();

    @Unique private float auram$zoomBeforeInput;
    @Unique private int auram$textureChunks = MinimapZoom.STOCK_TEXTURE_CHUNKS;

    @Inject(method = "init", at = @At(value = "INVOKE", target =
            "Ldev/ftb/mods/ftbchunks/client/FTBChunksClientConfig;init()V"))
    private void auram$allowWiderZoomBeforeConfigLoad(CallbackInfo callback) {
        // Widen the range before FTB reads and clamps a saved minimap zoom.
        FTBChunksClientConfig.MINIMAP_ZOOM.range(MinimapZoom.EXTRA_WIDE_ZOOM, 4.0);
    }

    @Inject(method = "changeZoom", at = @At("HEAD"))
    private void auram$captureCurrentZoom(boolean zoomIn, CallbackInfoReturnable<?> callback) {
        auram$zoomBeforeInput = getZoom();
    }

    @WrapOperation(method = "changeZoom", at = @At(value = "INVOKE", target =
            "Ldev/ftb/mods/ftblibrary/snbt/config/DoubleValue;set(Ljava/lang/Double;)V"))
    private void auram$setZoomTarget(DoubleValue config, Double stockTarget, Operation<Void> set,
                                    @Local(argsOnly = true) boolean zoomIn) {
        // Key repeat and reversals should start at the current view, not the previous destination.
        prevZoom = auram$zoomBeforeInput;
        set.call(config, MinimapZoom.keyTarget(config.get(), zoomIn, stockTarget));
    }

    @ModifyArg(method = "getZoom", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/util/Mth;lerp(DDD)D"), index = 0)
    private double auram$easeZoom(double progress) {
        return MinimapZoom.easeOut(progress);
    }

    @Inject(method = "renderHud", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
            target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;minimapTextureId:I"))
    private void auram$resizeMinimapTexture(CallbackInfo callback) {
        int chunks = MinimapZoom.textureChunks(getZoom(), FTBChunksClientConfig.MINIMAP_ZOOM.get());
        if (chunks != auram$textureChunks) {
            auram$textureChunks = chunks;
            if (minimapTextureId != -1) {
                TextureUtil.releaseTextureId(minimapTextureId);
                // FTB allocates a new texture and queues its full upload immediately below.
                minimapTextureId = -1;
            }
        }
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(intValue = 240), require = 2)
    private int auram$texturePixels(int original) {
        return auram$textureChunks * 16;
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(intValue = 15), require = 2)
    private int auram$uploadSpan(int original) {
        return auram$textureChunks;
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(intValue = 7), require = 2)
    private int auram$uploadRadius(int original) {
        return auram$textureChunks / 2;
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(doubleValue = 15.0), require = 2)
    private double auram$uvCenterSpan(double original) {
        return auram$textureChunks;
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(floatValue = 15.0F))
    private float auram$uvRadiusSpan(float original) {
        return auram$textureChunks;
    }
}
