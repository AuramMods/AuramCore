package art.arcane.auram.smoke.mixin;

import art.arcane.auram.smoke.MovementWaitProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MovementWaitProbeMixin {
    @Shadow public ServerPlayer player;

    @WrapMethod(method = "handleMovePlayer")
    private void auramSmoke$timeMovement(ServerboundMovePlayerPacket packet, Operation<Void> original) {
        if (!MovementWaitProbe.tracks(player)) {
            original.call(packet);
            return;
        }
        MovementWaitProbe.begin(player, packet);
        try {
            original.call(packet);
        } finally {
            MovementWaitProbe.end(player);
        }
    }
}
