package art.arcane.auram.smoke.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityBlockPositionProbe {
    @Accessor("blockPosition")
    void auramSmoke$setCachedPosition(BlockPos position);

    @Accessor("wasTouchingWater")
    void auramSmoke$setInWater(boolean inWater);
}
