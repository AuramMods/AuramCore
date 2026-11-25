package art.arcane.auram.mixin.botania;

import art.arcane.auram.compat.botania.EndoflameBurnState;
import art.arcane.auram.vapor.BotaniaVapor;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vazkii.botania.common.block.flower.generating.EndoflameBlockEntity;

@Mixin(value = EndoflameBlockEntity.class, remap = false)
abstract class EndoflameEffectsMixin implements EndoflameBurnState {
    @Shadow private int burnTime;
    @Unique private boolean auram$locallyEmitting;

    @Override
    public boolean auram$isBurning() {
        return burnTime > 0;
    }

    @Inject(method = "tickFlower", at = @At("RETURN"))
    private void auram$ventWhileBurning(CallbackInfo callback) {
        EndoflameBlockEntity flower = (EndoflameBlockEntity) (Object) this;
        Level level = flower.getLevel();
        if (level == null || (!auram$isBurning() && !auram$locallyEmitting)) return;
        auram$locallyEmitting = BotaniaVapor.endoflame(level, flower.getBlockPos(), flower.getEffectivePos(), auram$isBurning());
    }
}
