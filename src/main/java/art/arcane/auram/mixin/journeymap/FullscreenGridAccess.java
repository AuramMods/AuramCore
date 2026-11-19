package art.arcane.auram.mixin.journeymap;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Fullscreen.class, remap = false)
public interface FullscreenGridAccess {
    @Accessor("gridRenderer")
    static GridRenderer auram$grid() {
        throw new AssertionError();
    }
}
