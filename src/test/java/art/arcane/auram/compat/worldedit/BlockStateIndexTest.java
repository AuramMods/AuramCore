package art.arcane.auram.compat.worldedit;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class BlockStateIndexTest {
    @Test
    public void resolvesCanonicalStatesForEveryPropertyAndValue() {
        var values = Map.of("facing", List.of("north", "east", "south", "west"),
                "half", List.of("top", "bottom"), "waterlogged", List.of(false, true));
        var canonical = new HashMap<Map<String, Object>, Object>();
        for (var facing : values.get("facing")) {
            for (var half : values.get("half")) {
                for (var waterlogged : values.get("waterlogged")) {
                    canonical.put(Map.of("facing", facing, "half", half, "waterlogged", waterlogged), new Object());
                }
            }
        }
        var index = new BlockStateIndex(canonical);
        for (var state : canonical.keySet()) {
            int ordinal = index.ordinal(state);
            for (var property : values.entrySet()) {
                for (var value : property.getValue()) {
                    var expected = new HashMap<>(state);
                    expected.put(property.getKey(), value);
                    assertSame(canonical.get(expected), index.neighbor(ordinal, property.getKey(), value));
                }
            }
            assertNull(index.neighbor(ordinal, "facing", "up"));
            assertNull(index.neighbor(ordinal, "absent", true));
            assertNull(index.neighbor(ordinal, "half", null));
            assertNull(index.neighbor(ordinal, null, true));
        }
    }

    @Test
    public void supportsPropertylessBlocks() {
        var index = new BlockStateIndex(Map.of(Map.of(), new Object()));
        assertNull(index.neighbor(index.ordinal(Map.of()), "facing", "east"));
    }
}
