package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Explosion.class)
public interface ExplosionRadiusAccess {
    @Accessor("radius")
    float auram$getRadius();
}
