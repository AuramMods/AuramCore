package art.arcane.auram.vapor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** World-space Vapor sources. Invoke on the logical server thread. */
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
