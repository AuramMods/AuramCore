package art.arcane.auram.map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class SurfaceTileTest {
    private static final ResourceLocation DIMENSION = new ResourceLocation("test", "surface");

    @Test
    public void negativeChunkCoordinatesKeepTheirTileAndSlot() {
        var key = SurfaceTile.Key.atChunk(DIMENSION, -1, -9);
        assertEquals(-1, key.x());
        assertEquals(-2, key.z());
        assertEquals(63, SurfaceTile.slot(-1, -9));
        assertEquals(new ChunkPos(-1, -9), key.chunkAt(63));
    }

    @Test
    public void partialTileRoundTripsAndOnlyChangesRevisionWhenPixelsChange() throws IOException {
        var tile = new SurfaceTile();
        int[] colors = new int[SurfaceTile.CHUNK_PIXELS];
        Arrays.fill(colors, 0xff1177bb);
        assertTrue(tile.put(0, colors));
        assertTrue(tile.put(63, colors));
        assertFalse(tile.put(63, colors));
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        tile.write(encoded);
        SurfaceTile copy = SurfaceTile.read(new ByteArrayInputStream(encoded.toByteArray()));
        assertEquals(tile.version(), copy.version());
        assertEquals(2, copy.version().revision());
        assertTrue(copy.version().contains(63));
        assertFalse(copy.version().contains(1));
        assertEquals(0xff1177bb, copy.pixel(63, 15, 15));
        assertEquals(0, copy.pixel(1, 0, 0));
    }

    @Test
    public void fullIncompressibleTileFitsPacketAndTruncationFails() throws IOException {
        var tile = new SurfaceTile();
        Random random = new Random(1);
        int[] colors = new int[SurfaceTile.CHUNK_PIXELS];
        for (int slot = 0; slot < 64; slot++) {
            for (int i = 0; i < colors.length; i++) {
                colors[i] = random.nextInt();
            }
            tile.put(slot, colors);
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        tile.write(encoded);
        assertTrue(encoded.size() <= SurfaceTile.MAX_ENCODED_BYTES);
        byte[] truncated = Arrays.copyOf(encoded.toByteArray(), encoded.size() - 17);
        assertThrows(IOException.class, () -> SurfaceTile.read(new ByteArrayInputStream(truncated)));
    }
}
