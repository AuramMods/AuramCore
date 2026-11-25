package art.arcane.auram.field;

import art.arcane.auram.field.ClientFieldSources.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ClientFieldSourcesTest {
    private record Source(Key key, int rate) {}

    @Test
    public void legacyDeduplicationPreservesOtherProvidersAndAdditionalSourcesUsingTheSameVent() {
        var vent = new BlockPos(3, 65, 9);
        var heater = new Key(ResourceLocation.fromNamespaceAndPath("test", "heater"), vent);
        var other = new Key(ResourceLocation.fromNamespaceAndPath("test", "other"), vent);
        var remote = List.of(new Source(heater, 1), new Source(heater, 2), new Source(other, 3));
        assertEquals(List.of(2, 3, 4), ClientFieldSources.mergeLegacy(remote, List.of(new Source(heater, 4)),
                Source::key, Source::rate));
        assertEquals(List.of(3, 4, 5), ClientFieldSources.mergeLegacy(remote,
                List.of(new Source(heater, 4), new Source(heater, 5)), Source::key, Source::rate));
    }
}
