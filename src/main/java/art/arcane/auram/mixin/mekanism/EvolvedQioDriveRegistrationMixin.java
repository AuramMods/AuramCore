package art.arcane.auram.mixin.mekanism;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "fr.iglee42.evolvedmekanism.registries.EMItems", remap = false)
public abstract class EvolvedQioDriveRegistrationMixin {
    @Inject(method = "<clinit>", at = @At(value = "FIELD", target =
            "Lfr/iglee42/evolvedmekanism/tiers/storage/EMQIODriveTier;BOOSTED:Lmekanism/common/tier/QIODriveTier;"),
            require = 0)
    private static void auram$initializeDriveTiersBeforeReadingThem(CallbackInfo ci) {
        // Evolved Mekanism populates its drive tiers from the Mekanism enum's static initializer.
        // JEI can initialize EMItems before that enum has been initialized.
        try {
            Class.forName("mekanism.common.tier.QIODriveTier");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Evolved Mekanism requires Mekanism's QIO drive tiers", e);
        }
    }
}
