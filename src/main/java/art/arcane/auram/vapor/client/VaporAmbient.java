package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Local emitters that can use the client's existing block and entity state. */
final class VaporAmbient {
    private static final int BLOCK_SAMPLES_PER_TICK = 32;
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 4;
    private static final int MAX_BURNING_ENTITIES = 32;
    private static final float LAVA_PUFF = 0.50f;
    private static final float MAGMA_PUFF = LAVA_PUFF * 0.5f;
    private static final float TORCH_PUFF = 0.03f;
    private static final float BURNING_ENTITY_RATE = 0.15f;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final EntityTypeTest<Entity, Entity> ALL_ENTITIES = EntityTypeTest.forClass(Entity.class);

    record Sources(List<VaporVolume.Source> puffs, List<VaporVolume.Source> burning) {
        static final Sources EMPTY = new Sources(List.of(), List.of());

        boolean empty() {
            return puffs.isEmpty() && burning.isEmpty();
        }
    }

    private VaporAmbient() {
    }

    static Sources sample(ClientLevel level, BlockPos camera) {
        List<VaporVolume.Source> puffs = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < BLOCK_SAMPLES_PER_TICK; i++) {
            pos.set(camera.getX() + offset(HORIZONTAL_RANGE), camera.getY() + offset(VERTICAL_RANGE),
                    camera.getZ() + offset(HORIZONTAL_RANGE));
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            boolean lava = state.getFluidState().is(FluidTags.LAVA);
            if (lava || state.is(Blocks.MAGMA_BLOCK)) {
                // Surface puffs start above the fluid or solid block.
                BlockPos vent = pos.above();
                if (!level.isOutsideBuildHeight(vent) && level.getFluidState(vent).isEmpty()) {
                    puffs.add(new VaporVolume.Source(VaporClient.position(vent), lava ? LAVA_PUFF : MAGMA_PUFF, 0));
                }
            } else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                    || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                puffs.add(new VaporVolume.Source(VaporClient.position(pos), TORCH_PUFF, 0));
            }
        }

        var origin = VoxelGrid.originAround(camera.getX(), camera.getY(), camera.getZ());
        int side = VaporVolume.SIDE;
        AABB window = new AABB(origin.x(), origin.y(), origin.z(), origin.x() + side, origin.y() + side, origin.z() + side);
        List<Entity> entities = new ArrayList<>();
        level.getEntities(ALL_ENTITIES, window, Entity::isOnFire, entities, MAX_BURNING_ENTITIES);
        if (entities.isEmpty()) return puffs.isEmpty() ? Sources.EMPTY : new Sources(puffs, List.of());

        List<VaporVolume.Source> burning = entities.stream().limit(MAX_BURNING_ENTITIES)
                .map(entity -> new VaporVolume.Source(VaporClient.position(entity.blockPosition()), BURNING_ENTITY_RATE, 0))
                .toList();
        return new Sources(puffs, burning);
    }

    private static int offset(int range) {
        return RANDOM.nextInt(range * 2 + 1) - range;
    }
}
