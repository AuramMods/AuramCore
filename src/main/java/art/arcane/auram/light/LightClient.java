package art.arcane.auram.light;

import art.arcane.auram.Auram;
import art.arcane.auram.field.FieldNetwork;
import art.arcane.auram.field.client.FieldInterestClient;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.vapor.ThaumaturgeVapor;
import art.arcane.auram.vapor.VaporNetwork;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class LightClient {
    private static final int ALEMBIC_COLOR = 0xE5D5FF;
    private static final float ALEMBIC_STRENGTH = 3f / 15f;
    private static ClientLevel alembicLevel;
    private static List<LightSample> alembics = List.of();
    private static ClientLevel remoteLevel;
    private static List<LightSample> remote = List.of();

    private LightClient() {}

    @Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> LightNetwork.setReceiver(frame ->
                    Minecraft.getInstance().execute(() -> receive(frame))));
        }
    }

    private static void receive(LightNetwork.Frame frame) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FieldInterestClient.enabled(FieldNetwork.LIGHT)
                || !level.dimension().location().equals(frame.dimension())) return;
        remoteLevel = level;
        remote = frame.sources();
    }

    public static void setAlembicLights(ClientLevel level, List<VaporNetwork.Emitter> emitters) {
        // The server's short-lived Vapor sources already carry the active vents and their lifetime.
        alembicLevel = level;
        alembics = alembicSamples(emitters);
    }

    static List<LightSample> alembicSamples(List<VaporNetwork.Emitter> emitters) {
        return emitters.stream().filter(emitter -> emitter.id().equals(ThaumaturgeVapor.ALEMBIC))
                .map(emitter -> new LightSample(emitter.position(), ALEMBIC_COLOR, ALEMBIC_STRENGTH, 0, 0))
                .toList();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == alembicLevel) {
            alembicLevel = null;
            alembics = List.of();
        }
        if (event.getLevel() == remoteLevel) clearRemote();
    }

    public static void clearRemote() {
        remoteLevel = null;
        remote = List.of();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase == TickEvent.Phase.END && level != null) LightSources.expireLocal(level);
    }

    public static boolean hasSources(ClientLevel level) {
        return (level == remoteLevel && !remote.isEmpty()) || (level == alembicLevel && !alembics.isEmpty())
                || !LightSources.local(level).isEmpty();
    }

    public static void collect(ClientLevel level, Position origin, Int2IntOpenHashMap colors) {
        long tick = level.getGameTime();
        float partial = Minecraft.getInstance().getFrameTime();
        if (level == remoteLevel) {
            for (LightSample light : remote) add(level, origin, colors, light, tick, partial);
        }
        if (level == alembicLevel) {
            for (LightSample light : alembics) add(level, origin, colors, light, tick, partial);
        }
        for (LightSample light : LightSources.local(level)) add(level, origin, colors, light, tick, partial);
    }

    private static void add(ClientLevel level, Position origin, Int2IntOpenHashMap colors, LightSample light,
                            long tick, float partial) {
        var pos = light.position();
        if (level.hasChunkAt(pos)) putSource(origin, pos, light.packedColor(tick, partial), colors);
    }

    public static void putSource(Position origin, BlockPos pos, int packed,
                                 Int2IntOpenHashMap colors) {
        int x = pos.getX() - origin.x(), y = pos.getY() - origin.y(), z = pos.getZ() - origin.z();
        if (!BLOCKS.contains(x, y, z)) return;
        int cell = BLOCKS.index(x, y, z);
        if ((packed >>> 24) > (colors.get(cell) >>> 24)) colors.put(cell, packed);
    }
}
