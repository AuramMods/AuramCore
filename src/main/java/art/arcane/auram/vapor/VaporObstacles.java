package art.arcane.auram.vapor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class VaporObstacles {
    private static final ResourceLocation ENDER_IO_CONDUIT = ResourceLocation.fromNamespaceAndPath("enderio", "conduit");

    private VaporObstacles() {
    }

    public static boolean blocksVapor(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) return true;
        // Ender IO can report a full cube for a bundle with a facade or before its conduits sync.
        return !state.getBlock().builtInRegistryHolder().is(ENDER_IO_CONDUIT)
                && state.isCollisionShapeFullBlock(level, pos);
    }
}
