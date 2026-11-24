package art.arcane.auram.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** World-space Wind sources. Call from the logical server thread. */
public final class Wind {
    private Wind() {}

    /** Adds a single impulse. Only nearby clients simulate it. Direction is normalized by the API. */
    public static boolean gust(ServerLevel level, BlockPos position, Vec3 direction, float magnitude) {
        requireServerThread(level);
        var flow = flow(position, direction, magnitude);
        return magnitude > 0 && WindSources.gust(level, flow);
    }

    /**
     * Creates or moves a continuous source scoped to the level and the pair of ID and block position.
     * The source expires when stopped, its chunk unloads, or its level closes.
     * An emitter which moves to a new block must stop the old position.
     */
    public static boolean setEmitter(ServerLevel level, ResourceLocation id, BlockPos position,
                                     Vec3 direction, float magnitude) {
        requireServerThread(level);
        Objects.requireNonNull(id, "id");
        var flow = flow(position, direction, magnitude);
        return magnitude == 0 ? WindSources.stop(level, id, position)
                : WindSources.set(level, new WindNetwork.Emitter(id, flow));
    }

    public static boolean stopEmitter(ServerLevel level, ResourceLocation id, BlockPos position) {
        requireServerThread(level);
        return WindSources.stop(level, Objects.requireNonNull(id, "id"), Objects.requireNonNull(position, "position"));
    }

    private static WindNetwork.Flow flow(BlockPos position, Vec3 direction, float magnitude) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(direction, "direction");
        if (!Float.isFinite(magnitude) || magnitude < 0 || magnitude > 1) {
            throw new IllegalArgumentException("Wind magnitude must be between 0 and 1");
        }
        double max = Math.max(Math.abs(direction.x), Math.max(Math.abs(direction.y), Math.abs(direction.z)));
        if (!Double.isFinite(max) || (max == 0 && magnitude > 0)) {
            throw new IllegalArgumentException("Nonzero Wind requires a finite, nonzero direction");
        }
        if (magnitude == 0) return new WindNetwork.Flow(position, 0, 0, 0);
        double x = direction.x / max, y = direction.y / max, z = direction.z / max;
        double scale = magnitude / Math.sqrt(x * x + y * y + z * z);
        return new WindNetwork.Flow(position, (float) (x * scale), (float) (y * scale), (float) (z * scale));
    }

    private static void requireServerThread(ServerLevel level) {
        if (!Objects.requireNonNull(level, "level").getServer().isSameThread()) {
            throw new IllegalStateException("Wind sources must be updated on the server thread");
        }
    }
}
