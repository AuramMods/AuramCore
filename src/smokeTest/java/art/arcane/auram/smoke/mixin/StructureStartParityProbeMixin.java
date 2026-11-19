package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.LithoStartParitySmoke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(Structure.class)
public abstract class StructureStartParityProbeMixin {
    @WrapOperation(method = "generate", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/Structure;findValidGenerationPoint(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;)Ljava/util/Optional;"))
    private Optional<Structure.GenerationStub> auramSmoke$captureStart(Structure structure, Structure.GenerationContext context,
                                                                       Operation<Optional<Structure.GenerationStub>> original) {
        var stub = original.call(structure, context);
        LithoStartParitySmoke.checked(structure, context, stub);
        return stub;
    }
}
