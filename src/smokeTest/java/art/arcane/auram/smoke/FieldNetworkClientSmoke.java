package art.arcane.auram.smoke;

import art.arcane.auram.field.ClientFieldSources;
import art.arcane.auram.rf.RfFlow;
import art.arcane.auram.vapor.client.VaporTexture;
import art.arcane.auram.rf.client.RfFieldTexture;
import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.vapor.VaporNetwork;
import art.arcane.auram.wind.WindNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class FieldNetworkClientSmoke {
    private static final FieldNetworkSmoke.Sample CLIENT = new FieldNetworkSmoke.Sample("client");
    private static String reconnect;
    private static int reconnectDelay;

    private FieldNetworkClientSmoke() {}

    @SubscribeEvent
    public static void phase(ClientChatReceivedEvent.System event) {
        if (!FieldNetworkSmoke.ENABLED) return;
        String message = event.getMessage().getString();
        if (message.startsWith("FIELD_NET_AUDIT ")) {
            audit(message.substring("FIELD_NET_AUDIT ".length()));
        } else if (message.startsWith(FieldNetworkSmoke.PHASE)) {
            String phase = message.substring(FieldNetworkSmoke.PHASE.length());
            CLIENT.advance(phase, Map.of());
            if (Boolean.getBoolean("auram.fields.verifyOwnership") && (phase.equals("steady") || phase.equals("stopped"))) {
                verifyOwned(phase);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void audit(String test) {
        try {
            var rfClient = Class.forName("art.arcane.auram.rf.client.RfFieldClient");
            var samplesMethod = rfClient.getDeclaredMethod("samples");
            samplesMethod.setAccessible(true);
            var rf = (List<RfFlow.Sample>) samplesMethod.invoke(null);
            var sessionField = Class.forName("art.arcane.auram.vapor.client.VaporClient").getDeclaredField("session");
            sessionField.setAccessible(true);
            var session = sessionField.get(null);
            Map<?, ?> remote = Map.of();
            if (session != null) {
                var field = session.getClass().getDeclaredField("remoteVapor");
                field.setAccessible(true);
                remote = (Map<?, ?>) field.get(session);
            }
            var sources = remote.values().stream().map(VaporNetwork.Emitter.class::cast).toList();
            boolean furnace = sources.stream().anyMatch(source -> source.id().getPath().equals("furnace")
                    && source.position().equals(new BlockPos(22, 149, 40)) && source.rate() == 0.14f);
            boolean intake = sources.stream().anyMatch(source -> source.id().getPath().equals("rf_input")
                    && !source.position().equals(new BlockPos(44, 149, 28)) && source.rate() > 0);
            if (test.equals("outside") && (!rf.isEmpty() || furnace || intake)) {
                throw new AssertionError("Old sources survived leaving the fixture: " + rf + " " + sources);
            }
            if (test.equals("vapor_only") && (!rf.isEmpty() || !intake)) {
                throw new AssertionError("RF opt-out lost machine intake or kept cable samples: " + rf + " " + sources);
            }
            if (test.equals("covered_furnace") && sources.stream().noneMatch(source -> source.id().getPath().equals("furnace")
                    && source.position().equals(new BlockPos(22, 148, 39)) && source.rate() == 0.14f)) {
                throw new AssertionError("Covered furnace did not use its front vent: " + sources);
            }
            if (test.equals("burn_without_cooking") && sources.stream().anyMatch(source -> source.id().getPath().equals("furnace"))) {
                throw new AssertionError("A furnace with fuel and no recipe emitted cooking vapor");
            }
            if (test.equals("active")) {
                boolean ender = flowing(rf, 52, 28, 0, true) && flowing(rf, 49, 24, 2, false);
                boolean mekanism = flowing(rf, 46, 22, 0, true) && flowing(rf, 43, 18, 2, false);
                auditMachines();
                if (!furnace || !intake || !ender || !mekanism || !active(VaporTexture.class) || !active(RfFieldTexture.class)) {
                    throw new AssertionError("Missing shader inputs in active fixture: " + rf + " " + sources
                            + " vaporTexture=" + active(VaporTexture.class) + " rfTexture=" + active(RfFieldTexture.class));
                }
            }
            if (test.equals("arm_reconnect")) {
                var connection = Minecraft.getInstance().getConnection().getConnection();
                var peer = (InetSocketAddress) connection.getRemoteAddress();
                reconnect = peer.getAddress().getHostAddress() + ":" + peer.getPort();
                reconnectDelay = 20;
            }
            if (test.equals("disable_rf") || test.equals("enable_rf")) {
                var client = Class.forName("art.arcane.auram.field.client.FieldInterestClient");
                boolean light = (boolean) client.getMethod("enabled", int.class).invoke(null, 4);
                client.getMethod("configure", boolean.class, boolean.class, boolean.class)
                        .invoke(null, true, test.equals("enable_rf"), light);
            }
            System.out.println("FIELD_AUDIT_PASS " + test + " rf_edges=" + rf.size() + " server_vapor=" + sources.size());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void auditMachines() throws ReflectiveOperationException {
        var localMachines = Class.forName("art.arcane.auram.vapor.client.LocalMachineVapor");
        var method = localMachines.getDeclaredMethod("collect", net.minecraft.client.multiplayer.ClientLevel.class,
                VoxelGrid.Position.class);
        method.setAccessible(true);
        var client = Minecraft.getInstance();
        var player = client.player.blockPosition();
        var origin = VoxelGrid.originAround(player.getX(), player.getY(), player.getZ());
        var sources = ((Map<?, ?>) method.invoke(null, client.level, origin)).values().stream()
                .map(VaporNetwork.Emitter.class::cast).toList();
        long floor = sources.stream().filter(source -> source.id().getPath().equals("tconstruct/smeltery")
                && source.position().getY() == 148 && source.rate() == 0.12f).count();
        boolean mill = sources.stream().anyMatch(source -> source.id().getPath().equals("enderio/machine")
                && source.position().equals(new BlockPos(38, 149, 46)) && source.rate() == 0.08f);
        if (floor != 9 || !mill) throw new AssertionError("Missing local smeltery floor or SAG Mill vapor: " + sources);
    }

    private static boolean flowing(List<RfFlow.Sample> rf, int x, int z, int axis, boolean positive) {
        var edge = new RfFlow.Edge(new BlockPos(x, 148, z).asLong(), axis);
        return rf.stream().anyMatch(sample -> sample.edge().equals(edge)
                && (positive ? sample.forward() > 0 : sample.backward() > 0));
    }

    private static boolean active(Class<?> texture) throws ReflectiveOperationException {
        var state = texture.getDeclaredField("active");
        state.setAccessible(true);
        return state.getBoolean(null);
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
            if (ModList.get().isLoaded("thaumaturge")) checkRate(vapor, new BlockPos(26, 148, 44), 0.05f);
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
        if (!FieldNetworkSmoke.ENABLED || event.phase != TickEvent.Phase.END) return;
        CLIENT.end();
        var client = Minecraft.getInstance();
        if (reconnect != null && client.level == null && client.getConnection() == null && --reconnectDelay == 0) {
            String address = reconnect;
            reconnect = null;
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                    new ServerData("Field fixture", address, false), false);
        }
    }
}
