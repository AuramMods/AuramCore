package art.arcane.auram.smoke.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Register a test player without opening a network connection. */
@Mixin(PlayerList.class)
public interface PlayerListAccess {
    @Accessor("players") List<ServerPlayer> auramSmoke$players();
    @Accessor("playersByUUID") Map<UUID,ServerPlayer> auramSmoke$playersByUUID();
}
