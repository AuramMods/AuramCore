package art.arcane.auram.mixin.customnetherportals;

import art.arcane.auram.util.PortalChunkPreparation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Entity.class)
abstract class EntityPortalSearchMixin {
    @Shadow protected boolean isInsidePortal;
    @Shadow protected int portalTime;
    @Shadow protected abstract Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel level, BlockPos origin,
                                                                                boolean toNether, WorldBorder border);

    @Unique private PortalChunkPreparation auram$portalSearch;

    @Inject(method = "handleNetherPortal", at = @At("HEAD"))
    private void auram$forgetSearchOnExit(CallbackInfo ci) {
        if (!isInsidePortal) auram$portalSearch = null;
    }

    @WrapOperation(method = "handleNetherPortal", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;isPassenger()Z"))
    private boolean auram$waitBeforePortalTransfer(Entity entity, Operation<Boolean> isPassenger,
                                                    @Local(ordinal = 0) int waitTime,
                                                    @Local(ordinal = 1) ServerLevel destination) {
        boolean passenger = isPassenger.call(entity);
        if (passenger || portalTime < waitTime) return passenger;

        WorldBorder border = destination.getWorldBorder();
        double scale = DimensionType.getTeleportationScale(entity.level().dimensionType(), destination.dimensionType());
        BlockPos origin = border.clampToBounds(entity.getX() * scale, entity.getY(), entity.getZ() * scale);
        var chunks = destination.getChunkSource();
        if (auram$portalSearch == null || !auram$portalSearch.matches(chunks, origin)) {
            auram$portalSearch = new PortalChunkPreparation(chunks, origin);
        }
        // Defer before vanilla sets the portal cooldown or Forge sends the dimension transfer packets.
        if (!auram$portalSearch.ready(() -> getExitPortal(destination, origin, destination.dimension() == Level.NETHER, border))) {
            return true;
        }
        auram$portalSearch = null;
        return false;
    }
}
