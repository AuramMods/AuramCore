package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.vapor.VaporObstacles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Local emitters that can use the client's existing block and entity state. */
final class VaporAmbient {
    private static final int NEAR_SAMPLES_PER_TICK = 32;
    private static final int FAR_SAMPLES_PER_TICK = 128;
    private static final int BLOCK_RADIUS = 16;
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 4;
    private static final int MAX_BURNING_ENTITIES = 32;
    private static final float LAVA_PUFF = 0.10f;
    private static final float MAGMA_PUFF = 0.25f;
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
        for (int i = 0; i < NEAR_SAMPLES_PER_TICK; i++) {
            sampleBlock(level, pos.setWithOffset(camera, offset(HORIZONTAL_RANGE), offset(VERTICAL_RANGE),
                    offset(HORIZONTAL_RANGE)), puffs, true);
        }
        // Preserve the density of nearby puffs while extending the sample area.
        for (int i = 0; i < FAR_SAMPLES_PER_TICK; i++) {
            int x = offset(BLOCK_RADIUS), y = offset(BLOCK_RADIUS), z = offset(BLOCK_RADIUS);
            if (x * x + y * y + z * z > BLOCK_RADIUS * BLOCK_RADIUS || (Math.abs(y) <= VERTICAL_RANGE
                    && Math.abs(x) <= HORIZONTAL_RANGE && Math.abs(z) <= HORIZONTAL_RANGE)) continue;
            sampleBlock(level, pos.setWithOffset(camera, x, y, z), puffs, false);
        }

        var origin = VoxelGrid.originAround(camera.getX(), camera.getY(), camera.getZ());
        int side = VaporVolume.SIDE;
        AABB window = new AABB(origin.x(), origin.y(), origin.z(), origin.x() + side, origin.y() + side, origin.z() + side);
        List<Entity> entities = new ArrayList<>();
        // A blaze reports itself on fire when charging an attack. Its passive effect covers that visual.
        level.getEntities(ALL_ENTITIES, window, entity -> entity.isOnFire() && entity.getType() != EntityType.BLAZE,
                entities, MAX_BURNING_ENTITIES);
        List<VaporVolume.Source> burning = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            burning.add(new VaporVolume.Source(VaporClient.position(entity.blockPosition()), BURNING_ENTITY_RATE, 0));
        }
        VanillaEntityEffects.addVapor(level, window, puffs, burning);
        return puffs.isEmpty() && burning.isEmpty() ? Sources.EMPTY : new Sources(puffs, burning);
    }

    private static void sampleBlock(ClientLevel level, BlockPos pos, List<VaporVolume.Source> puffs, boolean sampleMagma) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        boolean lava = state.getFluidState().is(FluidTags.LAVA);
        if (lava || (sampleMagma && state.is(Blocks.MAGMA_BLOCK))) {
            BlockPos vent = pos.above();
            if (!level.isOutsideBuildHeight(vent) && !VaporObstacles.blocksVapor(level, vent, level.getBlockState(vent))) {
                puffs.add(new VaporVolume.Source(VaporClient.position(vent), lava ? LAVA_PUFF : MAGMA_PUFF, 0));
            }
        } else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            puffs.add(new VaporVolume.Source(VaporClient.position(pos), TORCH_PUFF, 0));
        }
    }

    private static int offset(int range) {
        return RANDOM.nextInt(range * 2 + 1) - range;
    }
}
