package art.arcane.auram.rf;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RfStateTest {
    @Test
    public void preservesBothDirectionsAndExactLargeAmounts() {
        var a = new RfFlow.Edge(1221, 1);
        var b = new RfFlow.Edge(2302, 2);
        var state = new RfState(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), false,
                List.of(new RfFlow.Sample(a, 100_000_003L, Long.MAX_VALUE)), List.of(b));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RfState.encode(state, buffer);
            assertEquals(state, RfState.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
