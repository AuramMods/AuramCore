package art.arcane.auram.mixin.ftbchunks;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LargeMapScreen.class, remap = false)
public interface LargeMapSurfaceAccess {
    @Accessor("regionPanel")
    RegionMapPanel auram$regionPanel();
}
