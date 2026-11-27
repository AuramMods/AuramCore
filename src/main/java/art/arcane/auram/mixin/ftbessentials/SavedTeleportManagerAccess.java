package art.arcane.auram.mixin.ftbessentials;

import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = SavedTeleportManager.class, remap = false)
public interface SavedTeleportManagerAccess {
    @Accessor("destinations")
    Map<String, TeleportPos> auram$manualDestinations();
}
