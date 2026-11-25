package art.arcane.auram.vapor;

import art.arcane.auram.field.ClientFieldSources;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.vapor.client.VaporClient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** World-space Vapor sources. Invoke on the thread that owns the level. */
public final class Vapor {
    public static final int MAX_RADIUS = 12;

    private Vapor() {
    }

    /** Adds density in [0, 1] at one air cell. */
    public static boolean pulse(ServerLevel level, BlockPos position, float density) {
        return pulse(level, position, density, 0);
    }

    /** Adds a pulse with quadratic falloff through connected air within radius blocks. */
    public static boolean pulse(ServerLevel level, BlockPos position, float density, int radius) {
        assertServerThread(level);
        Objects.requireNonNull(position, "position");
        validateDensity(density);
        if (radius < 0 || radius > MAX_RADIUS) throw new IllegalArgumentException("Vapor radius out of range");
        return density > 0.0f && VaporSources.pulse(level, new VaporNetwork.Pulse(position.immutable(), density, radius));
    }

    /**
     * Creates or updates an emitter by its namespaced ID and anchor position.
     * Rate is added per simulation tick (normally 20 ticks per second).
     * Emitters live until stopped or the level closes, and suspend while their chunk is unloaded.
     */
    public static boolean setEmitter(ServerLevel level, ResourceLocation id, BlockPos position, float densityPerTick) {
        assertServerThread(level);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        validateDensity(densityPerTick);
        if (densityPerTick == 0.0f) return VaporSources.stop(level, id, position);
        return VaporSources.set(level, new VaporNetwork.Emitter(id, position.immutable(), densityPerTick));
    }

    public static boolean stopEmitter(ServerLevel level, ResourceLocation id, BlockPos position) {
        assertServerThread(level);
        return VaporSources.stop(level, Objects.requireNonNull(id, "id"), Objects.requireNonNull(position, "position"));
    }

    /** Uses this client's simulation. No message is sent to the server or other players. */
    public static boolean pulse(Level level, BlockPos position, float density) {
        return pulse(level, position, density, 0);
    }

    public static boolean pulse(Level level, BlockPos position, float density, int radius) {
        if (level instanceof ServerLevel server) return pulse(server, position, density, radius);
        ClientFieldSources.requireClientThread(level);
        var pulse = new VaporNetwork.Pulse(position.immutable(), density, radius);
        return density > 0 && VaporClient.localPulse(level, pulse);
    }

    /** Client-owned emitters are keyed by ID and block, and removed on chunk unload. */
    public static boolean setEmitter(Level level, ResourceLocation id, BlockPos position, float densityPerTick) {
        if (level instanceof ServerLevel server) return setEmitter(server, id, position, densityPerTick);
        return setLocalEmitter(level, id, position, position, densityPerTick);
    }

    /** Sets a client-owned source at a vent adjacent to its owning block. */
    public static boolean setLocalEmitter(Level level, ResourceLocation id, BlockPos anchor, BlockPos vent,
                                          float densityPerTick) {
        ClientFieldSources.requireClientThread(level);
        validateDensity(densityPerTick);
        var key = new Key(id, anchor);
        if (densityPerTick == 0) return ClientFieldSources.VAPOR.stop(level, key);
        if (anchor.distManhattan(vent) > 1) throw new IllegalArgumentException("Vapor vent must touch its anchor");
        if (level.isOutsideBuildHeight(vent)) return false;
        return ClientFieldSources.VAPOR.set(level, key, new VaporNetwork.Emitter(id, vent.immutable(), densityPerTick));
    }

    public static boolean stopEmitter(Level level, ResourceLocation id, BlockPos position) {
        if (level instanceof ServerLevel server) return stopEmitter(server, id, position);
        return ClientFieldSources.VAPOR.stop(level, new Key(id, position));
    }

    private static void assertServerThread(ServerLevel level) {
        if (!Objects.requireNonNull(level, "level").getServer().isSameThread()) {
            throw new IllegalStateException("Vapor sources must be updated on the server thread");
        }
    }

    static void validateDensity(float density) {
        if (!Float.isFinite(density) || density < 0.0f || density > 1.0f) {
            throw new IllegalArgumentException("Vapor density must be between 0 and 1");
        }
    }
}
