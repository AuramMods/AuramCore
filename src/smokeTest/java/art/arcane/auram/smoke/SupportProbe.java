package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.Optional;

public interface SupportProbe {
    void auramSmoke$setSupport(Optional<BlockPos> pos);
    Optional<BlockPos> auramSmoke$getSupport();
    BlockPos auramSmoke$getOnPos(float offset);
    void auramSmoke$setLevel(Level level);
}
