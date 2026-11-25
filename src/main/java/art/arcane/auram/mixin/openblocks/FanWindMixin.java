package art.arcane.auram.mixin.openblocks;

import art.arcane.auram.wind.OpenBlocksWind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "art.arcane.openblocks.block.entity.OBFanBlockEntity", remap = false)
abstract class FanWindMixin {
    @Shadow public abstract int power();
    @Shadow public abstract float yaw();

    @Unique private int auram$previousPower;
    @Unique private float auram$previousYaw;

    @Inject(method = "tick", at = @At("RETURN"))
    private static void auram$trackLocalWind(Level level, BlockPos pos, BlockState state, @Coerce BlockEntity fan,
                                             CallbackInfo callback) {
        if (level.isClientSide()) ((FanWindMixin) (Object) fan).auram$update(fan);
    }

    @Unique
    private void auram$update(BlockEntity fan) {
        int power = OpenBlocksWind.effectivePower(power());
        float yaw = yaw();
        if (power == auram$previousPower && yaw == auram$previousYaw) return;
        if (OpenBlocksWind.update(fan, power, yaw)) {
            auram$previousPower = power;
            auram$previousYaw = yaw;
        }
    }

    @Inject(method = {"setRemoved", "onChunkUnloaded"}, at = @At("HEAD"), require = 0)
    private void auram$removeLocalWind(CallbackInfo callback) {
        var fan = (BlockEntity) (Object) this;
        if (fan.getLevel() != null && fan.getLevel().isClientSide()) {
            OpenBlocksWind.update(fan, 0, 0);
            auram$previousPower = 0;
            auram$previousYaw = 0;
        }
    }
}
