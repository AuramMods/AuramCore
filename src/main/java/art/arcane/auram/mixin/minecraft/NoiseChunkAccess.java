package art.arcane.auram.mixin.minecraft;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface NoiseChunkAccess {
    @Invoker("getInterpolatedState") BlockState auram$interpolatedState();
}
