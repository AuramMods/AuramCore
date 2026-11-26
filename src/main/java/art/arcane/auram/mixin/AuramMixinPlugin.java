package art.arcane.auram.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class AuramMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String optionalTarget = optionalTarget(mixinClassName);
        if (optionalTarget == null) return true;
        try {
            ClassNode dependency = MixinService.getService().getBytecodeProvider().getClassNode(optionalTarget);
            if (mixinClassName.equals("art.arcane.auram.mixin.psychedelicraft.JeiFluidFocusMixin")) {
                return dependency.methods.stream().noneMatch(method ->
                        method.name.equals("createFocusLink") && method.desc.equals("(Ljava/util/Collection;)V"));
            }
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + optionalTarget, e);
        }
    }

    private static String optionalTarget(String mixinClassName) {
        if (mixinClassName.startsWith("art.arcane.auram.mixin.vapor.")) {
            return "net.irisshaders.iris.api.v0.IrisApi";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.thaumaturge.") && mixinClassName.endsWith("VaporMixin")) {
            return "com.leclowndu93150.thaumaturge.content.crucible.BlockEntityCrucible";
        }
        if (mixinClassName.equals("art.arcane.auram.mixin.tconstruct.BlissCastingPreviewMixin")) {
            return "net.irisshaders.iris.uniforms.CapturedRenderingState";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.tconstruct.")) {
            return "slimeknights.tconstruct.smeltery.block.entity.HeaterBlockEntity";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.mekanism.Rf")) {
            return "mekanism.common.content.network.EnergyNetwork";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.journeymap.")) {
            return "journeymap.client.JourneymapClient";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.ftbchunks.")) {
            return "dev.ftb.mods.ftbchunks.client.FTBChunksClient";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.customnetherportals.")) {
            return "com.leclowndu93150.custom_nether_portals.mixin.PortalForcerMixin";
        }
        // Some mixins depend on a second mod or target vanilla classes directly.
        return switch (mixinClassName) {
            case "art.arcane.auram.mixin.supplementaries.CartographerPreviewMixin",
                    "art.arcane.auram.mixin.supplementaries.AdventurerMapPreviewMixin" ->
                    "jeresources.collection.TradeList";
            case "art.arcane.auram.mixin.cyclic.JeiCraftingRegistrationMixin",
                    "art.arcane.auram.mixin.jerm.JeiRepairIngredientsMixin",
                    "art.arcane.auram.mixin.mekanism.JeiReplicatorRecipeMixin",
                    "art.arcane.auram.mixin.psychedelicraft.JeiFluidFocusMixin" ->
                    "mezz.jei.api.gui.builder.IRecipeLayoutBuilder";
            case "art.arcane.auram.mixin.botania.ManaPoolVaporMixin" ->
                    "vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity";
            case "art.arcane.auram.mixin.botania.EndoflameEffectsMixin" ->
                    "vazkii.botania.common.block.flower.generating.EndoflameBlockEntity";
            case "art.arcane.auram.mixin.botania.ManaPoolRendererMixin" -> "net.irisshaders.iris.api.v0.IrisApi";
            case "art.arcane.auram.mixin.inventoryparticles.ClickParticlesMixin",
                    "art.arcane.auram.mixin.inventoryparticles.CreativeSlotWrapperAccess" ->
                    "net.lopymine.ip.mixin.ScreenHandlerMixin";
            case "art.arcane.auram.mixin.terrablender.BiomeFillMixin" -> "terrablender.worldgen.noise.Area";
            case "art.arcane.auram.mixin.relics.FluidCollisionMixin" -> "it.hurts.sskirillss.relics.mixin.EntityMixin";
            case "art.arcane.auram.mixin.bettercaves.AquiferPositionMixin" ->
                    "com.yungnickyoung.minecraft.bettercaves.mixin.aquiferfix.AquiferMixin";
            case "art.arcane.auram.mixin.immersiveweathering.LeafPileSpeedMixin" ->
                    "com.ordana.immersive_weathering.blocks.LeafPileBlock";
            case "art.arcane.auram.mixin.psychedelicraft.MashTubFluidReadinessMixin" ->
                    "ivorius.psychedelicraft.mixin.EntityMashTubFluidMixin";
            case "art.arcane.auram.mixin.dimdoors.PlayerCollisionMixin" -> "org.dimdev.dimdoors.mixin.ServerPlayNetworkHandlerMixin";
            case "art.arcane.auram.mixin.distanthorizons.ChunkMapDemandMixin" ->
                    "com.seibel.distanthorizons.core.generation.WorldGenerationQueue";
            case "art.arcane.auram.mixin.voxy.LandingChunkDeliveryMixin" ->
                    "me.cortex.voxy.server.ServerLodService";
            case "art.arcane.auram.mixin.ftbessentials.RtpBiomeWaitMixin" -> "dev.ftb.mods.ftbessentials.command.TeleportCommands";
            case "art.arcane.auram.mixin.lithostitched.StructureStartTerrainMixin" ->
                    "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator";
            case "art.arcane.auram.mixin.veinst.VeinstToolMixin" ->
                    "net.mcreator.oreminer.procedures.IsSneakingProcedure";
            default -> null;
        };
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
