package art.arcane.auram.rf.client;

import art.arcane.auram.Auram;
import art.arcane.auram.field.FieldNetwork;
import art.arcane.auram.field.client.FieldInterestClient;
import art.arcane.auram.rf.RfFlow;
import art.arcane.auram.rf.RfNetwork;
import art.arcane.auram.rf.RfState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class RfFieldClient {
    private static final boolean PROBE = Boolean.getBoolean("auram.rf.probe");
    private static final List<RfFlow.Sample> EMPTY = List.of();
    private static final Map<RfFlow.Edge, RfFlow.Sample> MODERN = new LinkedHashMap<>();
    private static ClientLevel level;
    private static List<RfFlow.Sample> samples = EMPTY;
    private static long expiresAt;
    private static int session;

    private RfFieldClient() {}

    @Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                RfNetwork.setReceiver(frame -> Minecraft.getInstance().execute(() -> accept(frame)));
                FieldNetwork.setRfReceiver(state -> Minecraft.getInstance().execute(() -> acceptState(state)));
            });
        }
    }

    private static void accept(RfNetwork.Frame frame) {
        if (prepare(frame.dimension())) {
            expiresAt = level.getGameTime() + 20;
            replace(frame.edges());
        }
    }

    private static void acceptState(RfState state) {
        if (!prepare(state.dimension())) return;
        expiresAt = level.getGameTime() + 60;
        if (state.initial()) MODERN.clear();
        if (!state.initial() && state.changed().isEmpty() && state.removed().isEmpty()) return;
        for (var edge : state.removed()) MODERN.remove(edge);
        for (var sample : state.changed()) MODERN.put(sample.edge(), sample);
        replace(List.copyOf(MODERN.values()));
    }

    private static boolean prepare(ResourceLocation dimension) {
        ClientLevel current = Minecraft.getInstance().level;
        if (!FieldInterestClient.enabled(FieldNetwork.RF) || current == null
                || !dimension.equals(current.dimension().location())) return false;
        if (level != current) {
            reset();
            level = current;
        }
        return true;
    }

    private static void replace(List<RfFlow.Sample> next) {
        if (!samples.equals(next)) {
            samples = next;
            if (PROBE) com.mojang.logging.LogUtils.getLogger().info("RF field received {} edges", samples.size());
        }
    }

    public static void reset() {
        level = null;
        samples = EMPTY;
        MODERN.clear();
        session++;
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == level) reset();
    }

    static int session() {
        return session;
    }

    static List<RfFlow.Sample> samples() {
        if (level != Minecraft.getInstance().level || level == null || level.getGameTime() >= expiresAt) return EMPTY;
        return samples;
    }
}
