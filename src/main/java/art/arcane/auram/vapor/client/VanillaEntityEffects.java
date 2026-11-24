package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.vapor.VaporObstacles;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;

/** Shader effects sampled from the same entities the client uses for vanilla particles. */
public final class VanillaEntityEffects {
    private static final int MAX_ENTITIES = 32;
    private static final float ROCKET_RATE = 0.15f;
    private static final float ROCKET_PUFF = 0.05f;
    private static final float DRAGON_BREATH_RATE = 0.10f;
    private static final int DRAGON_BREATH_LIGHT = light(0xAD4EF0, 2);
    private static final Map<EntityType<?>, Effect> PASSIVE = Map.of(
            EntityType.END_CRYSTAL, new Effect(0.10f, 0),
            EntityType.STRAY, new Effect(0.05f, light(0x58DACD, 2)),
            EntityType.BLAZE, new Effect(0.10f, light(0xFFAD3B, 3)),
            EntityType.MAGMA_CUBE, new Effect(0.10f, light(0xFF5125, 3)));
    private static final RandomSource RANDOM = RandomSource.create();
    private static final EntityTypeTest<Entity, Entity> ALL = EntityTypeTest.forClass(Entity.class);

    private record Effect(float vapor, int light) {}

    private VanillaEntityEffects() {}

    static void addVapor(ClientLevel level, AABB window, List<VaporVolume.Source> puffs,
                         List<VaporVolume.Source> continuous) {
        var entities = nearby(level, window, entity -> PASSIVE.containsKey(entity.getType())
                || entity instanceof FireworkRocketEntity || activeDragonBreath(entity));
        for (Entity entity : entities) {
            if (entity instanceof FireworkRocketEntity rocket) {
                addTrail(rocket, puffs, continuous);
            } else if (entity instanceof AreaEffectCloud breath) {
                float angle = RANDOM.nextFloat() * Mth.TWO_PI;
                float distance = Mth.sqrt(RANDOM.nextFloat()) * breath.getRadius();
                BlockPos point = BlockPos.containing(breath.getX() + Mth.cos(angle) * distance, breath.getY(),
                        breath.getZ() + Mth.sin(angle) * distance);
                addPassive(level, point, DRAGON_BREATH_RATE, continuous);
            } else {
                addPassive(level, body(entity), PASSIVE.get(entity.getType()).vapor(), continuous);
            }
        }
    }

    private static void addPassive(ClientLevel level, BlockPos pos, float rate, List<VaporVolume.Source> continuous) {
        if (!open(level, pos)) pos = pos.above();
        if (open(level, pos)) continuous.add(new VaporVolume.Source(VaporClient.position(pos), rate, 0));
    }

    private static boolean open(ClientLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                && !VaporObstacles.blocksVapor(level, pos, level.getBlockState(pos));
    }

    private static void addTrail(FireworkRocketEntity rocket, List<VaporVolume.Source> puffs,
                                 List<VaporVolume.Source> continuous) {
        Vec3 end = rocket.position();
        Vec3 start = new Vec3(rocket.xo, rocket.yo, rocket.zo);
        if (end.y <= start.y) return;

        double distance = end.distanceTo(start);
        // Use only the most recent part of a long jump, such as a position correction.
        if (distance > 6.0) start = end.lerp(start, 6.0 / distance);
        int steps = Mth.ceil(Math.min(distance, 6.0) * 2.0);
        boolean puff = rocket.tickCount % 5 == 0;
        float rate = (puff ? ROCKET_RATE - ROCKET_PUFF : ROCKET_RATE) / steps;
        for (int step = 1; step <= steps; step++) {
            BlockPos point = BlockPos.containing(start.lerp(end, (double) step / steps));
            continuous.add(new VaporVolume.Source(VaporClient.position(point), rate, 0));
        }
        if (puff) puffs.add(new VaporVolume.Source(VaporClient.position(rocket.blockPosition()), ROCKET_PUFF, 0));
    }

    public static void collectLights(ClientLevel level, Position origin, Int2IntOpenHashMap colors) {
        var window = new AABB(origin.x(), origin.y(), origin.z(),
                origin.x() + BLOCKS.side(), origin.y() + BLOCKS.side(), origin.z() + BLOCKS.side());
        var entities = nearby(level, window, entity -> {
            Effect effect = PASSIVE.get(entity.getType());
            return (effect != null && effect.light() != 0) || activeDragonBreath(entity);
        });
        for (Entity entity : entities) {
            if (entity instanceof AreaEffectCloud breath) {
                addLight(origin, breath.blockPosition(), DRAGON_BREATH_LIGHT, colors);
                float offset = breath.getRadius() * 0.65f;
                if (offset < 1f) continue;
                for (int angle = 0; angle < 4; angle++) {
                    float radians = angle * Mth.HALF_PI + Mth.PI / 4;
                    var pos = BlockPos.containing(breath.getX() + offset * Mth.cos(radians), breath.getY(),
                            breath.getZ() + offset * Mth.sin(radians));
                    addLight(origin, pos, DRAGON_BREATH_LIGHT, colors);
                }
            } else {
                addLight(origin, body(entity), PASSIVE.get(entity.getType()).light(), colors);
            }
        }
    }

    private static List<Entity> nearby(ClientLevel level, AABB window, Predicate<Entity> filter) {
        List<Entity> entities = new ArrayList<>();
        level.getEntities(ALL, window, entity -> entity.isAlive() && filter.test(entity), entities, MAX_ENTITIES);
        return entities;
    }

    private static boolean activeDragonBreath(Entity entity) {
        return entity instanceof AreaEffectCloud cloud && !cloud.isWaiting()
                && cloud.getRadius() > 0 && cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH;
    }

    private static BlockPos body(Entity entity) {
        return BlockPos.containing(entity.getX(), entity.getY(0.5), entity.getZ());
    }

    private static void addLight(Position origin, BlockPos pos, int color, Int2IntOpenHashMap colors) {
        int x = pos.getX() - origin.x(), y = pos.getY() - origin.y(), z = pos.getZ() - origin.z();
        if (!BLOCKS.contains(x, y, z)) return;
        int index = BLOCKS.index(x, y, z);
        if ((color >>> 24) > (colors.get(index) >>> 24)) colors.put(index, color);
    }

    private static int light(int rgb, int level) {
        // Bliss decodes block light range as exp2(level) - 1 and the dynamic source as 96 * alpha.
        return Math.round(((1 << level) - 1) * (255f / 96f)) << 24 | rgb;
    }
}
