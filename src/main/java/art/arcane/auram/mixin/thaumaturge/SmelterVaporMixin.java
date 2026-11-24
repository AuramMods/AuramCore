package art.arcane.auram.mixin.thaumaturge;

import art.arcane.auram.vapor.ThaumaturgeVapor;
import com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntityAlembic;
import com.leclowndu93150.thaumaturge.content.essentia.smeltery.BlockEntitySmelter;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = BlockEntitySmelter.class, remap = false)
abstract class SmelterVaporMixin {
    @Shadow(remap = false)
    private Direction[] sidesWith(Direction facing, boolean auxiliary) {
        throw new AssertionError();
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE",
            target = "Lcom/leclowndu93150/thaumaturge/content/essentia/smeltery/BlockEntitySmelter;cookTimeFor(I)I"))
    private void auram$markSmelting(CallbackInfo callback, @Share("smelting") LocalBooleanRef smelting) {
        smelting.set(true);
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void auram$ventSmelter(ServerLevel level, BlockPos position, BlockState state, CallbackInfo callback,
                                  @Share("smelting") LocalBooleanRef smelting) {
        var machine = (BlockEntitySmelter) (Object) this;
        if (!machine.getBlockState().getValue(BlockStateProperties.LIT)) return;

        List<BlockPos> columns = new ArrayList<>(4);
        auram$addColumn(level, position, columns);
        for (Direction side : sidesWith(state.getValue(BlockStateProperties.HORIZONTAL_FACING), true)) {
            auram$addColumn(level, position.relative(side), columns);
        }
        if (columns.isEmpty()) {
            ThaumaturgeVapor.machine(machine, true);
        } else if (smelting.get()) {
            ThaumaturgeVapor.alembics(level, columns);
        }
    }

    @Unique
    private static void auram$addColumn(ServerLevel level, BlockPos base, List<BlockPos> columns) {
        BlockPos.MutableBlockPos cursor = base.mutable().move(Direction.UP);
        BlockPos top = null;
        while (!level.isOutsideBuildHeight(cursor) && level.hasChunkAt(cursor)
                && level.getBlockEntity(cursor) instanceof BlockEntityAlembic) {
            top = cursor.immutable();
            cursor.move(Direction.UP);
        }
        if (top != null) columns.add(top);
    }
}
