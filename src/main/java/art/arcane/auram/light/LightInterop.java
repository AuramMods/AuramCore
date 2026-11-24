package art.arcane.auram.light;

import art.arcane.auram.Auram;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import org.slf4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Forge IMC bootstrap for callers with no compile-time AuramCore dependency. */
@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LightInterop {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LightInterop() {}

    @SubscribeEvent
    public static void connect(InterModProcessEvent event) {
        event.getIMCStream("light_api_v1"::equals).forEach(message -> {
            try {
                @SuppressWarnings("unchecked")
                Consumer<BiConsumer<Level, CompoundTag>> accept =
                        (Consumer<BiConsumer<Level, CompoundTag>>) message.messageSupplier().get();
                accept.accept((level, request) -> submit(message.senderModId(), level, request));
            } catch (RuntimeException failure) {
                LOGGER.warn("Invalid light integration request from {}", message.senderModId(), failure);
            }
        });
    }

    private static void submit(String sender, Level level, CompoundTag tag) {
        BlockPos position = BlockPos.of(tag.getLong("pos"));
        ResourceLocation id = ResourceLocation.tryBuild(sender, tag.getString("id"));
        if (id == null || id.getPath().isEmpty()) throw new IllegalArgumentException("Invalid light emitter ID");
        switch (tag.getString("operation")) {
            case "set" -> Light.setEmitter(level, id, position, tag.getInt("rgb"), tag.getFloat("strength"));
            case "pulse" -> Light.pulse(level, id, position, tag.getInt("rgb"), tag.getFloat("strength"),
                    tag.getInt("durationTicks"));
            case "stop" -> Light.stopEmitter(level, id, position);
            default -> throw new IllegalArgumentException("Unknown light operation");
        }
    }
}
