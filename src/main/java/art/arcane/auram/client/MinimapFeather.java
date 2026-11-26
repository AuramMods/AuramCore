package art.arcane.auram.client;

import art.arcane.auram.Auram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MinimapFeather {
    private static ShaderInstance shader;

    private MinimapFeather() {}

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(Auram.MODID, "minimap_feather"), DefaultVertexFormat.POSITION_COLOR_TEX),
                loaded -> shader = loaded);
    }

    public static boolean isLoaded() {
        return shader != null;
    }

    public static void use(float centerU, float centerV, float uvRadius) {
        shader.safeGetUniform("MinimapBounds").set(centerU, centerV, uvRadius);
        RenderSystem.setShader(() -> shader);
    }
}
