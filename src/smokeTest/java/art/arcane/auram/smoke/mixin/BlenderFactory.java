package art.arcane.auram.smoke.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Blender.class)
public interface BlenderFactory {
    @Invoker("<init>")
    static Blender auramSmoke$create(Long2ObjectOpenHashMap<BlendingData> heights,
                                    Long2ObjectOpenHashMap<BlendingData> density) {
        throw new AssertionError("Mixin did not supply the Blender constructor");
    }
}
