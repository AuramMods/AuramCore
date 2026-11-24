package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Forge IMC bootstrap for callers with no compile-time AuramCore dependency. */
@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VaporInterop {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONNECT = "vapor_api_v1";

    private VaporInterop() {
    }

    @SubscribeEvent
    public static void connect(InterModProcessEvent event) {
        event.getIMCStream(CONNECT::equals).forEach(message -> {
            try {
                @SuppressWarnings("unchecked")
                Consumer<BiConsumer<ServerLevel, CompoundTag>> accept =
                        (Consumer<BiConsumer<ServerLevel, CompoundTag>>) message.messageSupplier().get();
                accept.accept((level, request) -> submit(message.senderModId(), level, request));
            } catch (RuntimeException failure) {
                LOGGER.warn("Invalid Vapor integration request from {}", message.senderModId(), failure);
            }
        });
    }

    private static void submit(String sender, ServerLevel level, CompoundTag tag) {
        BlockPos position = BlockPos.of(tag.getLong("pos"));
        switch (tag.getString("operation")) {
            case "pulse" -> Vapor.pulse(level, position, tag.getFloat("density"), tag.getInt("radius"));
            case "set", "stop" -> {
                ResourceLocation id = ResourceLocation.tryBuild(sender, tag.getString("id"));
                if (id == null || id.getPath().isEmpty()) throw new IllegalArgumentException("Invalid Vapor emitter ID");
                if ("stop".equals(tag.getString("operation"))) Vapor.stopEmitter(level, id, position);
                else Vapor.setEmitter(level, id, position, tag.getFloat("density"));
            }
            default -> throw new IllegalArgumentException("Unknown Vapor operation");
        }
    }
}
