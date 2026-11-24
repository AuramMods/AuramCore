package art.arcane.auram.mixin.extrautilities;

import art.arcane.auram.vapor.MachineVapor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "inzhefop.extrautilitiesrebirth.procedures.FuelGeneratorTick", remap = false)
abstract class FuelGeneratorVaporMixin {
    @WrapOperation(method = "execute", at = @At(value = "INVOKE",
            target = "Linzhefop/extrautilitiesrebirth/procedures/GeneratorOutput;finishTick(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;ID)V"))
    private static void auram$burningFuel(LevelAccessor world, BlockPos pos, int generated, double speed,
                                         Operation<Void> original) {
        original.call(world, pos, generated, speed);
        if (generated > 0 && world instanceof ServerLevel level) {
            var machine = level.getBlockEntity(pos);
            if (machine != null) MachineVapor.burningTaggedGenerator(machine);
        }
    }
}
