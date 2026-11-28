package art.arcane.auram.compat.adapt;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BooleanSupplier;

public final class FelledTreeXp {
    private static final ThreadLocal<Cut> ACTIVE_CUT = new ThreadLocal<>();

    private FelledTreeXp() {}

    public static boolean duringPlayerCut(BlockPos origin, LevelAccessor level, Player player, BooleanSupplier fell) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)
                || !AdaptApi.isRunning() || !(AdaptApi.content().skill(SkillAxes.ID).orElse(null) instanceof SkillAxes axes)) {
            return fell.getAsBoolean();
        }

        Cut previous = ACTIVE_CUT.get();
        // Felling can consume the last point of durability before it removes any blocks.
        var cut = new Cut(serverLevel, serverPlayer, origin.immutable(), axes, ModRefs.player(serverPlayer),
                ModRefs.item(serverPlayer.getMainHandItem().copy()));
        ACTIVE_CUT.set(cut);
        try {
            return fell.getAsBoolean();
        } finally {
            if (previous == null) ACTIVE_CUT.remove();
            else ACTIVE_CUT.set(previous);
        }
    }

    public static Break snapshot(Level level, BlockPos pos, Player player) {
        Cut cut = ACTIVE_CUT.get();
        // Adapt has already seen the initiating break through its ServerPlayerGameMode hook.
        if (cut == null || cut.level() != level || cut.player() != player || cut.origin().equals(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        BlockRef block = ModRefs.block(cut.level(), pos);
        ((BlockSnapshot) block).auram$rememberFelledState(state);
        return new Break(cut.axes(), cut.playerRef(), block, cut.tool());
    }

    public interface BlockSnapshot {
        void auram$rememberFelledState(BlockState state);
    }

    public static final class Break extends BlockBreakEvent {
        private final SkillAxes axes;
        private final ItemRef tool;

        private Break(SkillAxes axes, PlayerRef player, BlockRef block, ItemRef tool) {
            super(player, block, false, 0);
            this.axes = axes;
            this.tool = tool;
        }

        public ItemRef tool() {
            return tool;
        }

        public void credit() {
            // Use the normal Axes reward code without dispatching abilities that can break more blocks.
            axes.on(this);
            XpProvenance.recordBreak(block());
        }
    }

    private record Cut(ServerLevel level, ServerPlayer player, BlockPos origin, SkillAxes axes,
                       PlayerRef playerRef, ItemRef tool) {}
}
