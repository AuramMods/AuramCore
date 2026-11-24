package art.arcane.auram.rf.client;

import art.arcane.auram.Auram;
import art.arcane.auram.rf.RfFlow;
import art.arcane.auram.rf.RfNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class RfFieldClient {
    private static final boolean PROBE = Boolean.getBoolean("auram.rf.probe");
    private static final List<RfFlow.Sample> EMPTY = List.of();
    private static ClientLevel level;
    private static List<RfFlow.Sample> samples = EMPTY;
    private static long expiresAt;
    private static int session;

    private RfFieldClient() {}

    @Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> RfNetwork.setReceiver(frame -> Minecraft.getInstance().execute(() -> accept(frame))));
        }
    }

    private static void accept(RfNetwork.Frame frame) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null || !frame.dimension().equals(current.dimension().location())) return;
        if (level != current) {
            level = current;
            samples = EMPTY;
            session++;
        }
        expiresAt = level.getGameTime() + 20;
        if (!samples.equals(frame.edges())) {
            samples = frame.edges();
            if (PROBE) {
                com.mojang.logging.LogUtils.getLogger().info("RF field received {} edges", samples.size());
            }
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() != level) return;
        level = null;
        samples = EMPTY;
        session++;
    }

    static int session() {
        return session;
    }

    static List<RfFlow.Sample> samples() {
        if (level != Minecraft.getInstance().level || level == null || level.getGameTime() >= expiresAt) return EMPTY;
        return samples;
    }
}
