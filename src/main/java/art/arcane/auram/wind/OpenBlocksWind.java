package art.arcane.auram.wind;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;

public final class OpenBlocksWind {
    static final ResourceLocation FAN = ResourceLocation.fromNamespaceAndPath("open_blocks", "fan");
    private static final float FULL_POWER = 0.30f;

    private OpenBlocksWind() {}

    public static int effectivePower(int power) {
        return Settings.SPEC.isLoaded() && Settings.FORCE.get() > 0 && Settings.RANGE.get() > 0 ? power : 0;
    }

    public static boolean update(BlockEntity fan, int power, float yaw) {
        var level = fan.getLevel();
        if (level == null) return false;
        if (power == 0) {
            Wind.stopEmitter(level, FAN, fan.getBlockPos());
            return true;
        }
        double angle = Math.toRadians(yaw);
        return Wind.setEmitter(level, FAN, fan.getBlockPos(),
                new Vec3(Math.sin(angle), 0, -Math.cos(angle)), FULL_POWER * power / 15.0f);
    }

    private static final class Settings {
        static final ForgeConfigSpec SPEC;
        static final ForgeConfigSpec.DoubleValue FORCE;
        static final ForgeConfigSpec.DoubleValue RANGE;

        static {
            try {
                var config = Class.forName("art.arcane.openblocks.config.OBServerConfig");
                SPEC = (ForgeConfigSpec) config.getField("SPEC").get(null);
                FORCE = (ForgeConfigSpec.DoubleValue) config.getField("FAN_FORCE").get(null);
                RANGE = (ForgeConfigSpec.DoubleValue) config.getField("FAN_RANGE").get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot read synced OpenBlocks fan settings", error);
            }
        }
    }
}
