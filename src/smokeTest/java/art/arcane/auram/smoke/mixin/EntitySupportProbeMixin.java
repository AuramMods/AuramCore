package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.SupportProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntitySupportProbeMixin implements SupportProbe {
    @Accessor("mainSupportingBlockPos") public abstract void auramSmoke$setSupport(Optional<BlockPos> pos);
    @Accessor("mainSupportingBlockPos") public abstract Optional<BlockPos> auramSmoke$getSupport();
    @Invoker("getOnPos") public abstract BlockPos auramSmoke$getOnPos(float offset);
    @Invoker("setLevel") public abstract void auramSmoke$setLevel(Level level);
}
