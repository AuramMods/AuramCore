package art.arcane.auram.waystone;

import dev.ftb.mods.ftbessentials.util.SavedTeleportManager;
import dev.ftb.mods.ftbessentials.util.TeleportPos;
import net.blay09.mods.waystones.api.IWaystone;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.blay09.mods.waystones.core.WaystoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WaystoneTeleportPos extends TeleportPos {
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2};
    private final SavedTeleportManager manager;
    private final String name;
    private final IWaystone original;

    public WaystoneTeleportPos(SavedTeleportManager manager, String name, IWaystone stone) {
        super(stone.getDimension(), stone.getPos());
        this.manager = manager;
        this.name = name;
        this.original = stone;
    }

    @Override
    public TeleportResult teleport(ServerPlayer player) {
        // Essentials can warm up a teleport. Re-check the route and blocks at execution time.
        IWaystone stone = WaystoneDestinations.forManager(manager).get(name);
        if (stone == null || !stone.getWaystoneUid().equals(original.getWaystoneUid())
                || WaystoneDestinations.manual(manager).containsKey(name)) return TeleportResult.UNKNOWN_DESTINATION;

        ServerLevel level = player.getServer().getLevel(stone.getDimension());
        if (level == null) return TeleportResult.DIMENSION_NOT_FOUND;
        BlockPos center = stone.getPos();
        IWaystone placed = WaystoneManager.get(player.getServer()).getWaystoneAt(level, center).orElse(null);
        if (placed == null || !stone.getWaystoneUid().equals(placed.getWaystoneUid())) {
            return TeleportResult.UNKNOWN_DESTINATION;
        }

        BlockState state = level.getBlockState(center);
        if (state.hasProperty(WaystoneBlockBase.FACING)) {
            BlockPos front = center.relative(state.getValue(WaystoneBlockBase.FACING));
            if (isSafe(level, player, front)) return new TeleportPos(level.dimension(), front).teleport(player);
        }
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy : HEIGHT_OFFSETS) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                        BlockPos target = center.offset(x, dy, z);
                        if (isSafe(level, player, target)) {
                            return new TeleportPos(level.dimension(), target).teleport(player);
                        }
                    }
                }
            }
        }
        return TeleportResult.failed(Component.translatable("auram.waystone.no_safe_landing", original.getName()));
    }

    private static boolean isSafe(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos floor = feet.below();
        BlockPos head = feet.above();
        if (level.isOutsideBuildHeight(floor) || level.isOutsideBuildHeight(head)
                || !level.getWorldBorder().isWithinBounds(feet)) return false;

        BlockState support = level.getBlockState(floor);
        if (!support.isFaceSturdy(level, floor, Direction.UP) || hazardous(support)
                || hazardous(level.getBlockState(feet)) || hazardous(level.getBlockState(head))) return false;
        var box = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
        return level.noCollision(player, box);
    }

    private static boolean hazardous(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
}
