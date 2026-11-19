package art.arcane.auram.smoke.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Comparator;

@Pseudo
@Mixin(targets = "com.teamabnormals.blueprint.common.world.modification.ModdedBiomeSource", remap = false)
public abstract class BlueprintBiomeOrderSmokeMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, remap = false)
    private static ArrayList<Pair<ResourceLocation, ?>> auramSmoke$stableSliceOrder(ArrayList<Pair<ResourceLocation, ?>> slices) {
        if (!Boolean.getBoolean("auram.smoke.orderBlueprintSlices")) return slices;
        // Blueprint builds these weighted slices from registry iteration.
        // Hold their order constant across disposable world comparisons.
        var ordered = new ArrayList<>(slices);
        ordered.sort(Comparator.comparing(slice -> slice.getFirst().toString()));
        return ordered;
    }
}
