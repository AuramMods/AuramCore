package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.FluidProbe;
import net.minecraft.world.entity.Entity;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public abstract class EntityFluidProbeMixin implements FluidProbe {
    @Shadow protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;
    @Shadow(remap = false) protected Object2DoubleMap<FluidType> forgeFluidTypeHeight;
    @Shadow protected boolean wasTouchingWater;

    @Invoker("updateInWaterStateAndDoFluidPushing")
    public abstract boolean auramSmoke$updateFluidState();

    public void auramSmoke$resetFluidState() {
        fluidHeight.clear();
        forgeFluidTypeHeight.clear();
        wasTouchingWater = false;
    }
}
