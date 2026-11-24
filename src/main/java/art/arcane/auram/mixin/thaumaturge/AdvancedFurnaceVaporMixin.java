package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.advancedfurnace.BlockEntityAdvancedAlchemicalFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityAdvancedAlchemicalFurnace.class, remap = false)
abstract class AdvancedFurnaceVaporMixin {
    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void auram$vapor(Level level, BlockPos pos, BlockState state, BlockEntityAdvancedAlchemicalFurnace machine,
                                    CallbackInfo callback) {
        ThaumaturgeVapor.machine(machine, machine.getBlockState().getValue(BlockStateProperties.LIT));
    }
}
