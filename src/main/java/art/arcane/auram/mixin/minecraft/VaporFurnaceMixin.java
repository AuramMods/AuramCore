package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.vapor.MachineVapor;
import art.arcane.auram.vapor.VaporDemonstrators;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class VaporFurnaceMixin {
    @Inject(method = "serverTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;litTime:I",
            opcode = Opcodes.PUTFIELD, ordinal = 0))
    private static void auram$vaporBurning(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                            CallbackInfo callback) {
        if (!auram$isVanillaFurnace(furnace)) MachineVapor.burning(furnace);
    }

    @Inject(method = "serverTick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;cookingProgress:I",
            opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private static void auram$vaporCooking(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                            CallbackInfo callback, @Share("vaporCooking") LocalBooleanRef cooking) {
        cooking.set(true);
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void auram$vaporEmitter(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                            CallbackInfo callback, @Share("vaporCooking") LocalBooleanRef cooking) {
        if (world instanceof ServerLevel level && auram$isVanillaFurnace(furnace)) {
            VaporDemonstrators.furnace(level, pos, state, cooking.get());
        }
    }

    @Unique
    private static boolean auram$isVanillaFurnace(AbstractFurnaceBlockEntity furnace) {
        var type = furnace.getType();
        return type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER;
    }
}
