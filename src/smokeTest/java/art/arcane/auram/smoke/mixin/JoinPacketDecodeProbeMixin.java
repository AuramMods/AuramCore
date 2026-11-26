package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.JoinPacketSample;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(PacketDecoder.class)
abstract class JoinPacketDecodeProbeMixin {
    @WrapMethod(method = "decode")
    private void auram$measureDecode(ChannelHandlerContext context, ByteBuf input, List<Object> decoded,
                                     Operation<Void> original) {
        if (!JoinPacketSample.ENABLED) {
            original.call(context, input, decoded);
            return;
        }
        var event = new JoinPacketSample();
        int start = input.readerIndex();
        int count = decoded.size();
        event.begin();
        original.call(context, input, decoded);
        event.end();
        if (decoded.size() > count) event.complete("decode", decoded.get(count), input.readerIndex() - start);
    }
}
