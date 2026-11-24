package art.arcane.auram.mixin.tconstruct;

import art.arcane.auram.vapor.TinkersVapor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

@Mixin(value = CastingBlockEntity.class, remap = false)
abstract class VaporCastingMixin {
    @Shadow private ICastingRecipe currentRecipe;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void auram$vapor(Level world, BlockPos pos, CallbackInfo ci) {
        var casting = (CastingBlockEntity) (Object) this;
        if (world instanceof ServerLevel level && currentRecipe != null && !casting.getTank().isEmpty()) {
            TinkersVapor.casting(level, pos);
        }
    }
}
