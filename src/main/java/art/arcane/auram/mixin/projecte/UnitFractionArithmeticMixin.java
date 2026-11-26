package art.arcane.auram.mixin.projecte;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import moze_intel.projecte.emc.arithmetic.FullBigFractionArithmetic;
import moze_intel.projecte.shaded.org.apache.commons.math3.fraction.BigFraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FullBigFractionArithmetic.class, remap = false)
abstract class UnitFractionArithmeticMixin {
    @WrapOperation(method = "mul", at = @At(value = "INVOKE", target =
            "Lmoze_intel/projecte/shaded/org/apache/commons/math3/fraction/BigFraction;multiply(J)"
                    + "Lmoze_intel/projecte/shaded/org/apache/commons/math3/fraction/BigFraction;"))
    private BigFraction auram$skipUnitProduct(BigFraction value, long multiplier, Operation<BigFraction> original) {
        return multiplier == 1 ? value : original.call(value, multiplier);
    }

    @WrapOperation(method = "div", at = @At(value = "INVOKE", target =
            "Lmoze_intel/projecte/shaded/org/apache/commons/math3/fraction/BigFraction;divide(J)"
                    + "Lmoze_intel/projecte/shaded/org/apache/commons/math3/fraction/BigFraction;"))
    private BigFraction auram$skipUnitDivision(BigFraction value, long divisor, Operation<BigFraction> original) {
        return divisor == 1 ? value : original.call(value, divisor);
    }
}
