package art.arcane.auram.mixin.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntitySupportMixin {
    @Shadow private Optional<BlockPos> mainSupportingBlockPos;
    @Shadow private boolean onGroundNoBlocks;
    @Shadow private BlockState feetBlockState;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void auram$invalidateSupportOnLevelChange(Level destination, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel && entity.level() != destination) {
            auram$clearSupport();
        }
    }

    @Inject(method = {"absMoveTo(DDD)V", "moveTo(DDDFF)V"}, at = @At("HEAD"))
    private void auram$invalidateSupportOnRelocation(CallbackInfo ci) {
        if (((Entity) (Object) this).level() instanceof ServerLevel) {
            auram$clearSupport();
        }
    }

    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"))
    private void auram$discardUnavailableSupport(float offset, CallbackInfoReturnable<BlockPos> ci) {
        if (((Entity) (Object) this).level() instanceof ServerLevel level && mainSupportingBlockPos.isPresent()) {
            BlockPos support = mainSupportingBlockPos.get();
            if (level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(support.getX()),
                    SectionPos.blockToSectionCoord(support.getZ())) == null) {
                // A cached contact in an unavailable chunk cannot describe current
                // terrain. Let vanilla calculate the position from the entity's
                // current coordinates instead of synchronously loading that contact.
                auram$clearSupport();
            }
        }
    }

    @Unique
    private void auram$clearSupport() {
        mainSupportingBlockPos = Optional.empty();
        onGroundNoBlocks = false;
        feetBlockState = null;
    }
}
