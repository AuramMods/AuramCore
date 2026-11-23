package art.arcane.auram.mixin.customnetherportals;

import art.arcane.auram.util.PortalChunkPreparation;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(value = PortalForcer.class, priority = 1500)
abstract class PortalCandidateMixin {
    @Shadow @Final protected ServerLevel level;

    @TargetHandler(mixin = "com.leclowndu93150.custom_nether_portals.mixin.PortalForcerMixin",
            name = "findClosestPortalPositionWithCustomPortals")
    @WrapOperation(method = "@MixinSquared:Handler", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;ensureLoadedAndValid(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;I)V"))
    private void auram$keepPoiValidationIndependent(PoiManager pois, LevelReader level, BlockPos origin, int radius,
                                                      Operation<Void> validate) {
        // POI validation can pump unrelated chunk work. Only guard the portal block reads that follow it.
        PortalChunkPreparation.outsideLookup(() -> validate.call(pois, level, origin, radius));
    }

    @TargetHandler(mixin = "com.leclowndu93150.custom_nether_portals.mixin.PortalForcerMixin",
            name = "findClosestPortalPositionWithCustomPortals")
    @WrapOperation(method = "@MixinSquared:Handler", remap = false, at = @At(value = "INVOKE", ordinal = 1,
            target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private Stream<BlockPos> auram$checkClosestCandidateFirst(Stream<BlockPos> candidates, Predicate<BlockPos> valid,
                                                               Operation<Stream<BlockPos>> filter,
                                                               @Local(argsOnly = true) BlockPos origin) {
        // The first filter is the world border check. The second filter reads the portal blocks.
        var ordered = candidates.sorted(Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(origin))
                .thenComparingInt(BlockPos::getY));
        return filter.call(ordered, valid).limit(1);
    }

    @WrapMethod(method = "createPortal")
    private Optional<BlockUtil.FoundRectangle> auram$leaveCreationToActualTransfer(
            BlockPos origin, Direction.Axis axis, Operation<Optional<BlockUtil.FoundRectangle>> create) {
        PortalChunkPreparation.finishBeforeCreation(level.getChunkSource());
        return create.call(origin, axis);
    }
}
