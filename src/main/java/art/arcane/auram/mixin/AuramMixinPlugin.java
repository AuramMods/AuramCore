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
            MixinService.getService().getBytecodeProvider().getClassNode(optionalTarget);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + optionalTarget, e);
        }
    }

    private static String optionalTarget(String mixinClassName) {
        if (mixinClassName.startsWith("art.arcane.auram.mixin.journeymap.")) {
            return "journeymap.client.JourneymapClient";
        }
        if (mixinClassName.startsWith("art.arcane.auram.mixin.ftbchunks.")) {
            return "dev.ftb.mods.ftbchunks.client.FTBChunksClient";
        }
        // These mixins target vanilla classes, so @Pseudo cannot make them optional.
        return switch (mixinClassName) {
            case "art.arcane.auram.mixin.botania.ManaPoolRendererMixin" -> "net.irisshaders.iris.api.v0.IrisApi";
            case "art.arcane.auram.mixin.terrablender.BiomeFillMixin" -> "terrablender.worldgen.noise.Area";
            case "art.arcane.auram.mixin.relics.FluidCollisionMixin" -> "it.hurts.sskirillss.relics.mixin.EntityMixin";
            case "art.arcane.auram.mixin.psychedelicraft.MashTubFluidReadinessMixin" ->
                    "ivorius.psychedelicraft.mixin.EntityMashTubFluidMixin";
            case "art.arcane.auram.mixin.dimdoors.PlayerCollisionMixin" -> "org.dimdev.dimdoors.mixin.ServerPlayNetworkHandlerMixin";
            case "art.arcane.auram.mixin.distanthorizons.ChunkMapDemandMixin" ->
                    "com.seibel.distanthorizons.core.generation.WorldGenerationQueue";
            case "art.arcane.auram.mixin.ftbessentials.RtpBiomeWaitMixin" -> "dev.ftb.mods.ftbessentials.command.TeleportCommands";
            case "art.arcane.auram.mixin.lithostitched.StructureStartTerrainMixin" ->
                    "dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawGenerator";
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
