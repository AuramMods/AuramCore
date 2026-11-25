package art.arcane.auram.light;

import art.arcane.auram.field.ClientFieldSources;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Colored shader light sources. Call on the thread that owns the level. */
public final class Light {
    private Light() {}

    /**
     * Creates or updates a light at this ID and block position. A strength of zero removes it.
     * With a client level, the source is local and belongs to the anchor chunk.
     */
    public static boolean setEmitter(Level level, ResourceLocation id, BlockPos position, int rgb, float strength) {
        return set(level, id, position, rgb, strength, 0);
    }

    /** Replaces this source with a light that fades from the given strength over 1 to 1200 ticks. */
    public static boolean pulse(Level level, ResourceLocation id, BlockPos position, int rgb, float strength,
                                int durationTicks) {
        if (durationTicks < 1 || durationTicks > LightSample.MAX_PULSE_TICKS) {
            throw new IllegalArgumentException("Light pulse duration must be between 1 and 1200 ticks");
        }
        return set(level, id, position, rgb, strength, durationTicks);
    }

    /** Removes this source. Remove the previous position when moving an emitter to another block. */
    public static boolean stopEmitter(Level level, ResourceLocation id, BlockPos position) {
        requireLevelThread(level);
        return LightSources.stop(level, new LightSources.Key(id, position));
    }

    private static boolean set(Level level, ResourceLocation id, BlockPos position, int rgb, float strength,
                               int duration) {
        requireLevelThread(level);
        var key = new LightSources.Key(id, position);
        if (!Float.isFinite(strength) || strength < 0f || strength > 1f) {
            throw new IllegalArgumentException("Light strength must be between 0 and 1");
        }
        if ((rgb & 0xff000000) != 0) throw new IllegalArgumentException("Light color must be 0xRRGGBB");
        if (strength == 0f || rgb == 0) return LightSources.stop(level, key);
        if (level.isOutsideBuildHeight(position) || !level.hasChunkAt(position)) return false;
        // A server pulse may be queued just before the level clock advances and sends its frame.
        int dispatchDelay = level instanceof ServerLevel ? 1 : 0;
        long endsAt = duration == 0 ? 0 : level.getGameTime() + duration + dispatchDelay;
        return LightSources.set(level, key, new LightSample(key.position(), rgb, strength, endsAt, duration));
    }

    private static void requireLevelThread(Level level) {
        if (Objects.requireNonNull(level, "level") instanceof ServerLevel server) {
            if (!server.getServer().isSameThread()) {
                throw new IllegalStateException("Light sources must be updated on the level thread");
            }
        } else {
            ClientFieldSources.requireClientThread(level);
        }
    }
}
