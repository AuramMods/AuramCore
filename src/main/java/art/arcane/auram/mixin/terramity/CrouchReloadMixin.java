package art.arcane.auram.mixin.terramity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {
        "net.mcreator.terramity.procedures.AdvancedAutomaticRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.AdvancedBurstRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.AdvancedPistolRightclickedProcedure",
        "net.mcreator.terramity.procedures.AntiMaterialRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.AntimatterRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.AsphodelRightclickedProcedure",
        "net.mcreator.terramity.procedures.BasicPistolRightclickedProcedure",
        "net.mcreator.terramity.procedures.BasicRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.BigIronRightclickedProcedure",
        "net.mcreator.terramity.procedures.CelestialSixtyRightclickedProcedure",
        "net.mcreator.terramity.procedures.ConductiteLaserRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.CosmicStormRightclickedProcedure",
        "net.mcreator.terramity.procedures.DavyJonesRightclickedProcedure",
        "net.mcreator.terramity.procedures.DivineInterventionRightclickedProcedure",
        "net.mcreator.terramity.procedures.EliteRifleRightclickedProcedure",
        "net.mcreator.terramity.procedures.FiveThousandMagnumRightclickedProcedure",
        "net.mcreator.terramity.procedures.FortunesFavorRightclickedProcedure",
        "net.mcreator.terramity.procedures.GaiasTempestRightclickedProcedure",
        "net.mcreator.terramity.procedures.HellfireFlurryRightclickedProcedure",
        "net.mcreator.terramity.procedures.NihilityRightclickedProcedure",
        "net.mcreator.terramity.procedures.OlympusRightclickedProcedure",
        "net.mcreator.terramity.procedures.OnyxStormRightclickedProcedure",
        "net.mcreator.terramity.procedures.PlaguePistolRightclickedProcedure",
        "net.mcreator.terramity.procedures.PlaguebringerRightclickedProcedure",
        "net.mcreator.terramity.procedures.PumpActionShotgunRightclickedProcedure",
        "net.mcreator.terramity.procedures.SawedOffShotgunRightclickedProcedure",
        "net.mcreator.terramity.procedures.SixShooterRightclickedProcedure",
        "net.mcreator.terramity.procedures.StairwayToHeavenRightclickedProcedure",
        "net.mcreator.terramity.procedures.SuppressedAdvancedPistolRightclickedProcedure",
        "net.mcreator.terramity.procedures.TitanomachyRightclickedProcedure"
}, remap = false)
public abstract class CrouchReloadMixin {
    // These guns use crouch-use to reload. Keep Terramity's hand check on the firing path.
    @Redirect(method = "execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;",
            remap = true))
    private static ItemStack auram$allowCrouchReload(LivingEntity shooter) {
        return shooter.isShiftKeyDown() ? ItemStack.EMPTY : shooter.getOffhandItem();
    }
}
