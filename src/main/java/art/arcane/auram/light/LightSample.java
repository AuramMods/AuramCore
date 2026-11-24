package art.arcane.auram.light;

import net.minecraft.core.BlockPos;

record LightSample(BlockPos position, int rgb, float strength, long endsAt, int duration) {
    static final int MAX_PULSE_TICKS = 1200;

    boolean expired(long tick) {
        return duration != 0 && tick >= endsAt;
    }

    int packedColor(long tick, float partialTick) {
        float envelope = duration == 0 ? 1f : Math.min(1f, Math.max(0f, (endsAt - tick - partialTick) / duration));
        int intensity = Math.round(255f * strength * envelope);
        return intensity == 0 ? 0 : intensity << 24 | rgb;
    }
}
