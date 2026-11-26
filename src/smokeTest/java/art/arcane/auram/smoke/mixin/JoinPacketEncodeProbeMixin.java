package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.JoinPacketSample;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketEncoder.class)
abstract class JoinPacketEncodeProbeMixin {
    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void auram$measureEncode(ChannelHandlerContext context, Packet<?> packet, ByteBuf output,
                                     Operation<Void> original) {
        if (!JoinPacketSample.ENABLED) {
            original.call(context, packet, output);
            return;
        }
        var event = new JoinPacketSample();
        int start = output.writerIndex();
        event.begin();
        original.call(context, packet, output);
        event.end();
        event.complete("encode", packet, output.writerIndex() - start);
    }
}
