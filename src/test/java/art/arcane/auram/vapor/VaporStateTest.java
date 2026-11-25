package art.arcane.auram.vapor;

import art.arcane.auram.field.ClientFieldSources.Key;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class VaporStateTest {
    @Test
    public void keepsTheAnchorAndVentSeparateInAnInitialUpdate() {
        var provider = ResourceLocation.fromNamespaceAndPath("auram", "test");
        var base = new BlockPos(-131, 108, 40);
        var vent = base.above();
        var key = new Key(provider, base);
        var state = new VaporState(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), true,
                List.of(new VaporState.Source(key, new VaporNetwork.Emitter(provider, vent, 0.12f))),
                List.of(new Key(provider, base.below())), List.of(new VaporNetwork.Pulse(vent, 0.4f, 2)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            VaporState.encode(state, buffer);
            assertEquals(state, VaporState.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
