package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.MinimapTerrainUpload;
import art.arcane.auram.client.MinimapZoom;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.snbt.config.DoubleValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

import java.lang.ref.WeakReference;

@Mixin(value = FTBChunksClient.class, remap = false)
abstract class MinimapZoomMixin {
    @Shadow private double prevZoom;
    @Shadow private int minimapTextureId;
    @Shadow private boolean updateMinimapScheduled;
    @Shadow private int currentPlayerChunkX;
    @Shadow private int currentPlayerChunkZ;
    @Shadow public abstract float getZoom();

    @Unique private float auram$zoomBeforeInput;
    @Unique private int auram$textureChunks = MinimapZoom.STOCK_TEXTURE_CHUNKS;
    @Unique private static final long AURAM$UPLOAD_PERIOD = 200_000_000L;
    @Unique private long auram$lastUpload;
    @Unique private WeakReference<ClientLevel> auram$uploadedLevel = new WeakReference<>(null);
    @Unique private WeakReference<MapDimension> auram$uploadedDimension = new WeakReference<>(null);

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
        if (minimapTextureId == -1) auram$uploadedLevel.clear();
    }

    @ModifyExpressionValue(method = "renderHud", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;updateMinimapScheduled:Z"))
    private boolean auram$refreshTerrain(boolean scheduled, @Local(name = "dim") MapDimension dimension,
                                         @Local(name = "cx") int chunkX, @Local(name = "cz") int chunkZ,
                                         @Local(name = "playerX") double playerX, @Local(name = "playerZ") double playerZ,
                                         @Local(name = "zoom0") float zoom, @Local(name = "minimapBlur") boolean blur) {
        var level = Minecraft.getInstance().level;
        boolean freshTexture = auram$uploadedLevel.get() != level || auram$uploadedDimension.get() != dimension;
        if (!scheduled && !freshTexture) return false;

        long now = System.nanoTime();
        if (!freshTexture && now - auram$lastUpload < AURAM$UPLOAD_PERIOD
                && auram$coversTerrain(playerX, playerZ, zoom)) {
            // Keep FTB's dirty flag set until the next upload.
            return false;
        }

        updateMinimapScheduled = false;
        MinimapTerrainUpload.upload(dimension, chunkX, chunkZ, auram$textureChunks, blur);
        currentPlayerChunkX = chunkX;
        currentPlayerChunkZ = chunkZ;
        auram$lastUpload = now;
        if (freshTexture) {
            auram$uploadedLevel = new WeakReference<>(level);
            auram$uploadedDimension = new WeakReference<>(dimension);
        }
        return false; // The texture was already refreshed in region-sized rectangles.
    }

    @Unique
    private boolean auram$coversTerrain(double x, double z, float zoom) {
        // FTB displays 112 blocks of radius at zoom 1. Reserve half a texel for linear filtering.
        double available = auram$textureChunks * 8.0 - 112.0 / zoom - 0.5;
        return Math.abs(x - currentPlayerChunkX * 16.0 - 8.0) <= available
                && Math.abs(z - currentPlayerChunkZ * 16.0 - 8.0) <= available;
    }

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", ordinal = 0,
            target = "Ldev/ftb/mods/ftblibrary/math/MathUtils;mod(DD)D"))
    private double auram$terrainU(double position, double chunkSize, Operation<Double> wrap) {
        return position - currentPlayerChunkX * chunkSize;
    }

    @WrapOperation(method = "renderHud", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ldev/ftb/mods/ftblibrary/math/MathUtils;mod(DD)D"))
    private double auram$terrainV(double position, double chunkSize, Operation<Double> wrap) {
        return position - currentPlayerChunkZ * chunkSize;
    }

    @ModifyConstant(method = "renderHud", constant = @Constant(intValue = 240), require = 2)
    private int auram$texturePixels(int original) {
        return auram$textureChunks * 16;
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
