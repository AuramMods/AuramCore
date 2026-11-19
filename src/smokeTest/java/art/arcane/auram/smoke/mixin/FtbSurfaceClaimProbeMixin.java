package art.arcane.auram.smoke.mixin;

import dev.ftb.mods.ftblibrary.math.XZ;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen", remap = false)
public interface FtbSurfaceClaimProbeMixin {
    @Accessor("selectedChunks")
    Set<XZ> auram$selection();
}
