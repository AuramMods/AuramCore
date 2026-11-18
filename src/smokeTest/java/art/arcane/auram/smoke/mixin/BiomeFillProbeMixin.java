package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.BiomeFillProbe;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.world.level.biome.BiomeResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FillBiomeCommand.class)
public abstract class BiomeFillProbeMixin {
    @Inject(method = "makeResolver", at = @At("RETURN"), cancellable = true)
    private static void auramSmoke$countResolver(CallbackInfoReturnable<BiomeResolver> ci) {
        ci.setReturnValue(BiomeFillProbe.wrap(ci.getReturnValue()));
    }
}
