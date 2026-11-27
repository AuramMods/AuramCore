package art.arcane.auram.mixin.ftbchunks;

import art.arcane.auram.client.CompassHeading;
import art.arcane.auram.client.MinimapWidgetCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftbchunks.api.client.minimap.MinimapContext;
import dev.ftb.mods.ftbchunks.api.client.minimap.MinimapInfoComponent;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.List;

@Mixin(value = FTBChunksClient.class, remap = false)
abstract class MinimapWidgetMixin {
    @Shadow private Matrix4f worldMatrix;
    @Shadow @Final private List<MinimapInfoComponent> sortedComponents;
    @Shadow private int currentPlayerChunkX;
    @Shadow private int currentPlayerChunkZ;

    @Shadow
    private void drawInWorldIcons(Minecraft mc, GuiGraphics graphics, float tickDelta,
                                  double playerX, double playerY, double playerZ, int width, int height) {
        throw new AssertionError();
    }

    @Unique private final MinimapWidgetCache auram$widget = new MinimapWidgetCache();
    @Unique private final CompassHeading auram$heading = new CompassHeading();
    @Unique private WeakReference<ClientLevel> auram$level = new WeakReference<>(null);

    @WrapMethod(method = "renderHud")
    private void auram$renderHud(GuiGraphics graphics, float tickDelta, Operation<Void> render) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != auram$level.get()) {
            auram$level = new WeakReference<>(level);
            auram$widget.invalidate();
            auram$heading.reset();
        }
        try {
            render.call(graphics, tickDelta);
        } finally {
            auram$widget.abort();
        }
    }

    @ModifyExpressionValue(method = "renderHud", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F", remap = true))
    private float auram$smoothHeading(float yaw) {
        return auram$heading.update(System.nanoTime(), yaw);
    }

    @Inject(method = "renderHud", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/Tesselator;getInstance()Lcom/mojang/blaze3d/vertex/Tesselator;",
            remap = true), cancellable = true)
    private void auram$prepareWidget(GuiGraphics graphics, float tickDelta, CallbackInfo ci,
                                    @Local(name = "mc") Minecraft mc, @Local(name = "dim") MapDimension dim,
                                    @Local(name = "playerX") double playerX, @Local(name = "playerY") double playerY,
                                    @Local(name = "playerZ") double playerZ,
                                    @Local(name = "scaledWidth") int width, @Local(name = "scaledHeight") int height,
                                    @Local(name = "x") int x, @Local(name = "y") int y,
                                    @Local(name = "size") int size, @Local(name = "alpha") int alpha,
                                    @Local(name = "rotationLocked") boolean rotationLocked) {
        if (rotationLocked) auram$heading.reset();
        if (!MinimapWidgetCache.ready()) return;
        var window = mc.getWindow();
        auram$widget.setLayout(new MinimapWidgetCache.Layout(x, y, size, window.getWidth(), window.getHeight(),
                width, height, FTBChunksClientConfig.SQUARE_MINIMAP.get(), rotationLocked, alpha));
        long now = System.nanoTime();
        if (!auram$widget.canDraw(now)) {
            auram$widget.replay(graphics);
            if (worldMatrix != null && FTBChunksClientConfig.IN_WORLD_WAYPOINTS.get()) {
                drawInWorldIcons(mc, graphics, tickDelta, playerX, playerY, playerZ, width, height);
            }
            ci.cancel();
            return;
        }

        // Text can extend well beyond the map or move above it when it is at the bottom.
        var context = new MinimapContext(mc, mc.player, dim, XZ.of(currentPlayerChunkX, currentPlayerChunkZ),
                new Vec3(playerX, playerY, playerZ), FTBChunksClientConfig.MINIMAP_SETTINGS.get());
        int minY = y - 24;
        int maxY = y + size + 24;
        int offset = 0;
        double fontScale = Math.max(1, FTBChunksClientConfig.MINIMAP_FONT_SCALE.get());
        for (MinimapInfoComponent component : sortedComponents) {
            if (!component.shouldRender(context)) continue;
            int componentHeight = component.height(context);
            if (y + size + componentHeight >= height) {
                minY = Math.min(minY, y - componentHeight - offset - 16);
            } else {
                maxY = Math.max(maxY, y + size + offset + (int) Math.ceil(componentHeight * fontScale) + 18);
            }
            offset += componentHeight;
        }
        auram$widget.begin(graphics, now, minY, maxY);
    }

    @Inject(method = "renderHud", at = @At(value = "FIELD",
            target = "Ldev/ftb/mods/ftbchunks/client/FTBChunksClient;worldMatrix:Lorg/joml/Matrix4f;",
            opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private void auram$finishWidget(GuiGraphics graphics, float tickDelta, CallbackInfo ci) {
        auram$widget.finish(graphics);
    }
}
