package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntitySmelter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntitySmelter.class, remap = false)
abstract class SmelterVaporMixin {
    @Inject(method = "serverTick", at = @At("RETURN"))
    private void auram$vapor(CallbackInfo callback) {
        var machine = (BlockEntitySmelter) (Object) this;
        ThaumaturgeVapor.machine(machine, machine.getBlockState().getValue(BlockStateProperties.LIT));
    }
}
