package art.arcane.auram.mixin.tweak;

import art.arcane.auram.tweak.FasterRandom;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldgenRandom.class)
public abstract class SlimeRandomSourceMixin {
    @WrapOperation(method = "seedSlimeChunk", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/util/RandomSource;create(J)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource auram$retainSlimeRandom(long seed, Operation<RandomSource> original) {
        return FasterRandom.enabled() ? new LegacyRandomSource(seed) : original.call(seed);
    }
}
