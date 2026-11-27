package art.arcane.auram.mixin.ftbessentials;

import dev.ftb.mods.ftbessentials.util.FTBEPlayerData;
import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SavedTeleportManager.HomeManager.class, remap = false)
public interface HomeManagerAccess {
    @Accessor("playerData")
    FTBEPlayerData auram$playerData();
}
