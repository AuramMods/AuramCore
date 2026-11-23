package art.arcane.auram.mixin.bettercaves;

import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer", priority = 1500)
abstract class AquiferPositionMixin {
    @Unique private static final String AURAM_BETTER_CAVES =
            "com.yungnickyoung.minecraft.bettercaves.mixin.aquiferfix.AquiferMixin";
    @Unique private BlockPos.MutableBlockPos auram$liquidSample;
    @Unique private ChunkPos auram$liquidChunk;

    @TargetHandler(mixin = AURAM_BETTER_CAVES, name = "bettercaves$fixAquiferLiquids")
    @Redirect(method = "@MixinSquared:Handler", remap = false,
            at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos auram$reuseSamplePosition(int x, int y, int z) {
        // This position is consumed immediately by the ChunkPos constructor.
        if (auram$liquidSample == null) {
            auram$liquidSample = new BlockPos.MutableBlockPos();
        }
        return auram$liquidSample.set(x, y, z);
    }

    @TargetHandler(mixin = AURAM_BETTER_CAVES, name = "bettercaves$fixAquiferLiquids")
    @Redirect(method = "@MixinSquared:Handler", remap = false,
            at = @At(value = "NEW", target = "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/ChunkPos;"))
    private ChunkPos auram$reuseChunkPosition(BlockPos pos) {
        int x = SectionPos.blockToSectionCoord(pos.getX());
        int z = SectionPos.blockToSectionCoord(pos.getZ());
        if (auram$liquidChunk == null || auram$liquidChunk.x != x || auram$liquidChunk.z != z) {
            auram$liquidChunk = new ChunkPos(x, z);
        }
        return auram$liquidChunk;
    }
}
