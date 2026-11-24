package art.arcane.auram.tconstruct;

import net.minecraft.util.Mth;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

public final class CastingCooling {
    private CastingCooling() {}

    /** Filling is fully hot. Once the timer starts, the residual heat falls to zero. */
    public static float remainingHeat(CastingBlockEntity casting) {
        int coolingTime = casting.getCoolingTime();
        if (coolingTime <= 0) return 1.0f;
        return Mth.clamp(1.0f - (float) casting.getTimer() / coolingTime, 0.0f, 1.0f);
    }
}
