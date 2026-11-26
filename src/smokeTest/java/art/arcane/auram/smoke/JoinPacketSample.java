package art.arcane.auram.smoke;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import net.minecraftforge.network.ICustomPacket;

@Name("auram.JoinPacket")
@Category({"Auram", "World Load"})
@StackTrace(false)
public final class JoinPacketSample extends Event {
    public static final boolean ENABLED = Boolean.getBoolean("auram.smoke.joinPackets");
    public String kind;
    public String stage;
    public long bytes;

    public void complete(String stage, Object packet, int bytes) {
        String name = packet.getClass().getSimpleName();
        this.kind = packet instanceof ICustomPacket<?> custom ? name + "/" + custom.getName() : name;
        this.stage = stage;
        this.bytes = bytes;
        commit();
    }
}
