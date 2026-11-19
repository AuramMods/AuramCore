package art.arcane.auram.mixin.journeymap;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.minimap.MiniMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MiniMap.class, remap = false)
public interface MiniMapGridAccess {
    @Accessor("gridRenderer")
    static GridRenderer auram$grid() {
        throw new AssertionError();
    }
}
