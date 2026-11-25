package art.arcane.auram.smoke;

import art.arcane.auram.field.ClientFieldSources;
import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.vapor.VaporNetwork;
import art.arcane.auram.wind.WindNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class FieldNetworkClientSmoke {
    private static final FieldNetworkSmoke.Sample CLIENT = new FieldNetworkSmoke.Sample("client");

    private FieldNetworkClientSmoke() {}

    @SubscribeEvent
    public static void phase(ClientChatReceivedEvent.System event) {
        if (!FieldNetworkSmoke.ENABLED) return;
        String message = event.getMessage().getString();
        if (message.startsWith(FieldNetworkSmoke.PHASE)) {
            String phase = message.substring(FieldNetworkSmoke.PHASE.length());
            CLIENT.advance(phase, Map.of());
            if (Boolean.getBoolean("auram.fields.verifyOwnership") && (phase.equals("steady") || phase.equals("stopped"))) {
                verifyOwned(phase);
            }
        }
    }

    private static void verifyOwned(String phase) {
        var client = Minecraft.getInstance();
        var level = client.level;
        var player = client.player;
        var pos = player.blockPosition();
        var origin = VoxelGrid.originAround(pos.getX(), pos.getY(), pos.getZ());
        var vapor = new ArrayList<VaporNetwork.Emitter>();
        var wind = new ArrayList<WindNetwork.Emitter>();
        ClientFieldSources.VAPOR.collect(level, origin, vapor);
        ClientFieldSources.WIND.collect(level, origin, wind);

        if (player.getGameProfile().getName().equals("FieldFar")) {
            checkRate(vapor, new BlockPos(199, 149, 42), 0.32f);
            if (!wind.isEmpty()) throw new AssertionError("The far client can see local fan wind");
        } else {
            boolean active = phase.equals("steady");
            checkRate(vapor, new BlockPos(24, 149, 38), 0.07f);
            checkRate(vapor, new BlockPos(25, 149, 40), active ? 0.32f : 0f);
            checkRate(vapor, new BlockPos(21, 149, 39), active ? 0.32f : 0f);
            checkRate(vapor, new BlockPos(23, 148, 42), active ? 0.65f : 0f);
            checkRate(vapor, new BlockPos(43, 148, 43), active ? 0.10f : 0f);
            if (wind.size() != (active ? 1 : 0) || active && Math.abs(wind.get(0).flow().x() - 0.30f) > 0.0001f) {
                throw new AssertionError("Incorrect local fan wind: " + wind);
            }
        }
        System.out.println("FIELD_LOCAL_OWNERSHIP " + phase);
    }

    private static void checkRate(ArrayList<VaporNetwork.Emitter> sources, BlockPos pos, float expected) {
        double rate = sources.stream().filter(source -> source.position().equals(pos))
                .mapToDouble(VaporNetwork.Emitter::rate).sum();
        if (Math.abs(rate - expected) > 0.0001f) {
            throw new AssertionError("Local vapor at " + pos + " is " + rate + ", expected " + expected + " from " + sources);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ClientTickEvent event) {
        if (FieldNetworkSmoke.ENABLED && event.phase == TickEvent.Phase.START) CLIENT.start();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ClientTickEvent event) {
        if (FieldNetworkSmoke.ENABLED && event.phase == TickEvent.Phase.END) CLIENT.end();
    }
}
