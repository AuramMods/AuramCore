package art.arcane.auram.light;

import art.arcane.auram.Auram;
import art.arcane.auram.field.VoxelGrid.Position;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
        if (level == null || !level.dimension().location().equals(frame.dimension())) return;
        remoteLevel = level;
        remote = frame.sources();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == remoteLevel) {
            remoteLevel = null;
            remote = List.of();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase == TickEvent.Phase.END && level != null) LightSources.expireLocal(level);
    }

    public static boolean hasSources(ClientLevel level) {
        return (level == remoteLevel && !remote.isEmpty()) || !LightSources.local(level).isEmpty();
    }

    public static void collect(ClientLevel level, Position origin, Int2IntOpenHashMap colors) {
        long tick = level.getGameTime();
        float partial = Minecraft.getInstance().getFrameTime();
        if (level == remoteLevel) {
            for (LightSample light : remote) add(level, origin, colors, light, tick, partial);
        }
        for (LightSample light : LightSources.local(level)) add(level, origin, colors, light, tick, partial);
    }

    private static void add(ClientLevel level, Position origin, Int2IntOpenHashMap colors, LightSample light,
                            long tick, float partial) {
        var pos = light.position();
        int x = pos.getX() - origin.x(), y = pos.getY() - origin.y(), z = pos.getZ() - origin.z();
        if (!BLOCKS.contains(x, y, z) || !level.hasChunkAt(pos)) return;
        int packed = light.packedColor(tick, partial);
        int cell = BLOCKS.index(x, y, z);
        if ((packed >>> 24) > (colors.get(cell) >>> 24)) colors.put(cell, packed);
    }
}
