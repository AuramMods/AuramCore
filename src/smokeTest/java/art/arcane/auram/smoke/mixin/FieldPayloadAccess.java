package art.arcane.auram.smoke.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundCustomPayloadPacket.class)
public interface FieldPayloadAccess {
    @Accessor("data")
    FriendlyByteBuf auramSmoke$payload();
}
