package art.arcane.auram.wind;

import art.arcane.auram.field.ClientFieldSources;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.vapor.client.VaporClient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** World-space Wind sources. Call from the thread that owns the level. */
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

    /** Adds a one-time impulse using the level's owning side. Client gusts stay local. */
    public static boolean gust(Level level, BlockPos position, Vec3 direction, float magnitude) {
        if (level instanceof ServerLevel server) return gust(server, position, direction, magnitude);
        ClientFieldSources.requireClientThread(level);
        var gust = flow(position, direction, magnitude);
        return magnitude > 0 && VaporClient.localGust(level, gust);
    }

    /** Client-owned emitters are keyed by ID and block, and removed on chunk unload. */
    public static boolean setEmitter(Level level, ResourceLocation id, BlockPos position,
                                     Vec3 direction, float magnitude) {
        if (level instanceof ServerLevel server) return setEmitter(server, id, position, direction, magnitude);
        var key = new Key(id, position);
        var flow = flow(position, direction, magnitude);
        if (magnitude == 0) return ClientFieldSources.WIND.stop(level, key);
        return ClientFieldSources.WIND.set(level, key, new WindNetwork.Emitter(id, flow));
    }

    public static boolean stopEmitter(Level level, ResourceLocation id, BlockPos position) {
        if (level instanceof ServerLevel server) return stopEmitter(server, id, position);
        return ClientFieldSources.WIND.stop(level, new Key(id, position));
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
