package art.arcane.auram.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.LoginWrapper;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(value = HandshakeHandler.class, remap = false)
abstract class ConfigSyncBatchMixin {
    @Unique private static final ResourceLocation CONFIG_CHANNEL = ResourceLocation.parse("fml:handshake");
    @Unique private static final int CONFIGS_PER_TICK = 8;
    @Unique private static final int MAX_PENDING_ACKS = 32;

    @Shadow private List<NetworkRegistry.LoginPayload> messageList;
    @Shadow private List<Integer> sentMessages;
    @Shadow private int packetPosition;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auram$synchronizePendingReplies(CallbackInfo callback) {
        // The server tick adds reply indexes while the network thread removes them.
        sentMessages = Collections.synchronizedList(sentMessages);
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/network/LoginWrapper;sendServerToClientLoginPacket(Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/network/FriendlyByteBuf;ILnet/minecraft/network/Connection;)V"))
    private void auram$sendQueuedForgeConfigs(LoginWrapper wrapper, ResourceLocation channel, FriendlyByteBuf data,
                                            int index, Connection connection, Operation<Void> send) {
        send.call(wrapper, channel, data, index, connection);
        if (!auram$isForgeConfig(messageList.get(index))) return;

        // Keep custom payloads and registry negotiation on Forge's normal schedule.
        int next = index + 1;
        int limit = Math.min(messageList.size(), index + CONFIGS_PER_TICK);
        while (next < limit && sentMessages.size() < MAX_PENDING_ACKS && connection.channel().isWritable()) {
            NetworkRegistry.LoginPayload config = messageList.get(next);
            if (!auram$isForgeConfig(config)) break;
            if (config.needsResponse()) sentMessages.add(next);
            send.call(wrapper, config.getChannelName(), config.getData(), next, connection);
            next++;
        }

        // Forge increments packetPosition once after this invocation returns.
        packetPosition = next - 1;
    }

    @Unique
    private static boolean auram$isForgeConfig(NetworkRegistry.LoginPayload payload) {
        return CONFIG_CHANNEL.equals(payload.getChannelName()) && payload.getMessageContext().startsWith("Config ");
    }
}
