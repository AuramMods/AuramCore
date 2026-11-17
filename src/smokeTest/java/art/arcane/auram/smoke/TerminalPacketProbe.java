package art.arcane.auram.smoke;

import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;

public final class TerminalPacketProbe {
    public static int overflowWrites;
    private static final ThreadLocal<List<CompoundTag>> ACTIVE=new ThreadLocal<>();
    public static void start() { ACTIVE.set(new ArrayList<>()); }
    public static List<CompoundTag> stop() { var value=ACTIVE.get();ACTIVE.remove();return value; }
    public static void record(CompoundTag packet) { var value=ACTIVE.get();if(value!=null)value.add(packet.copy()); }
}
