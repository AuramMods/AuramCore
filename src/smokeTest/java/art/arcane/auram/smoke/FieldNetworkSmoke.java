package art.arcane.auram.smoke;

import art.arcane.auram.light.Light;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class FieldNetworkSmoke {
    public static final boolean ENABLED = "field-network".equals(System.getProperty("auram.smoke"));
    public static final String PHASE = "FIELD_NET_PHASE ";
    private static final Gson JSON = new Gson();
    private static final Sample SERVER = new Sample("server");
    private static final Map<String, Map<String, Traffic>> PAYLOAD = new TreeMap<>();

    private static final BlockPos LIGHT = new BlockPos(36, 149, 43);
    private static final BlockPos POOL = new BlockPos(44, 148, 41);
    private static final BlockPos FIRST_CABLE = new BlockPos(40, 148, 22);
    private static final ResourceLocation LIGHT_ID = new ResourceLocation("auram_patch_smoke", "field_network");
    private static boolean driving;

    private FieldNetworkSmoke() {}

    public static synchronized void sent(String player, String channel, int payloadBytes) {
        if (SERVER.phase == null) return;
        var channels = PAYLOAD.computeIfAbsent(player, ignored -> new TreeMap<>());
        var traffic = channels.computeIfAbsent(channel, ignored -> new Traffic());
        traffic.packets++;
        traffic.bytes += payloadBytes;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        if (!ENABLED) return;
        event.getDispatcher().register(Commands.literal("fieldnet")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("phase", StringArgumentType.word()).executes(context -> {
                    String next = StringArgumentType.getString(context, "phase");
                    advanceServer(next);
                    for (var player : context.getSource().getServer().getPlayerList().getPlayers()) {
                        player.sendSystemMessage(Component.literal(PHASE + next));
                    }
                    System.out.println(PHASE + next);
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("fieldconduits")
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    try {
                        FieldNetworkConduits.configure(context.getSource().getLevel());
                        System.out.println("FIELD_CONDUITS_READY");
                        return 1;
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Could not configure the Ender IO fixture", error);
                    }
                }));
        event.getDispatcher().register(Commands.literal("fieldfixtures")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("active", BoolArgumentType.bool()).executes(context -> {
                    driving = BoolArgumentType.getBool(context, "active");
                    var level = context.getSource().getLevel();
                    Light.setEmitter(level, LIGHT_ID, LIGHT, 0x4790EF, driving ? 0.3f : 0f);
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void drive(TickEvent.LevelTickEvent event) {
        if (!ENABLED || !driving || event.phase != TickEvent.Phase.START
                || !(event.level instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) return;
        var cable = level.getBlockEntity(FIRST_CABLE);
        if (cable != null) {
            cable.getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                    .ifPresent(energy -> energy.receiveEnergy(80_000, false));
        }
        if (level.getBlockEntity(POOL) instanceof ManaPoolBlockEntity pool) pool.receiveMana(100);
    }


    private static synchronized void advanceServer(String next) {
        SERVER.advance(next, Map.of("pre_compression_payload", PAYLOAD));
        PAYLOAD.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tickStart(TickEvent.ServerTickEvent event) {
        if (ENABLED && event.phase == TickEvent.Phase.START) SERVER.start();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickEnd(TickEvent.ServerTickEvent event) {
        if (ENABLED && event.phase == TickEvent.Phase.END) SERVER.end();
    }

    private static final class Traffic {
        int packets;
        long bytes;
    }

    static final class Sample {
        private final String role;
        private final List<Long> tickNanos = new ArrayList<>();
        private String phase;
        private long start;

        Sample(String role) {
            this.role = role;
        }

        void start() {
            start = System.nanoTime();
        }

        void end() {
            if (phase != null && start != 0) tickNanos.add(System.nanoTime() - start);
            start = 0;
        }

        void advance(String next, Map<String, ?> details) {
            if (phase != null) {
                var result = new LinkedHashMap<String, Object>();
                result.put("event", "field_network_phase");
                result.put("status", "info");
                result.put("role", role);
                result.put("phase", phase);
                var sorted = tickNanos.stream().sorted().toList();
                result.put("ticks", sorted.size());
                result.put("median_ms", percentile(sorted, 0.50));
                result.put("p95_ms", percentile(sorted, 0.95));
                result.putAll(details);
                System.out.println("QA_EVT " + JSON.toJson(result));
            }
            phase = next.equals("done") ? null : next;
            tickNanos.clear();
            start = 0;
        }

        private static double percentile(List<Long> sorted, double percentile) {
            if (sorted.isEmpty()) return 0;
            return sorted.get((int) Math.ceil(sorted.size() * percentile) - 1) / 1e6;
        }
    }
}
