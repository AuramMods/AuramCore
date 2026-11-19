package art.arcane.auram.map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** One persisted and streamed tile. Coordinates are in tiles, with 8x8 chunks per tile. */
public final class SurfaceTile {
    public static final int CHUNKS = 8;
    public static final int CHUNK_PIXELS = 16 * 16;
    public static final int MAX_ENCODED_BYTES = CHUNKS * CHUNKS * CHUNK_PIXELS * Integer.BYTES + 256;
    private static final int MAGIC = 0x41534d01;
    private final int[] pixels = new int[CHUNKS * CHUNKS * CHUNK_PIXELS];
    private long coverage;
    private long revision;

    public record Key(ResourceLocation dimension, int x, int z) {
        public static Key atChunk(ResourceLocation dimension, int x, int z) {
            return new Key(dimension, Math.floorDiv(x, CHUNKS), Math.floorDiv(z, CHUNKS));
        }

        public ChunkPos chunkAt(int slot) {
            return new ChunkPos(x * CHUNKS + slot % CHUNKS, z * CHUNKS + slot / CHUNKS);
        }
    }

    public record Version(long revision, long coverage) {
        public boolean contains(int slot) {
            return (coverage & (1L << slot)) != 0;
        }
    }

    public static int slot(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, CHUNKS) + Math.floorMod(chunkZ, CHUNKS) * CHUNKS;
    }

    public Version version() {
        return new Version(revision, coverage);
    }

    public int pixel(int slot, int x, int z) {
        return pixels[slot * CHUNK_PIXELS + z * 16 + x];
    }

    /** The worker owns mutable tiles and increments the revision for each changed chunk. */
    boolean put(int slot, int[] colors) {
        int offset = slot * CHUNK_PIXELS;
        boolean changed = (coverage & (1L << slot)) == 0;
        for (int i = 0; i < CHUNK_PIXELS; i++) {
            changed |= pixels[offset + i] != colors[i];
            pixels[offset + i] = colors[i];
        }
        if (changed) {
            coverage |= 1L << slot;
            revision++;
        }
        return changed;
    }

    void write(OutputStream output) throws IOException {
        DataOutputStream header = new DataOutputStream(output);
        header.writeInt(MAGIC);
        header.writeLong(revision);
        header.writeLong(coverage);
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(output)))) {
            for (long remaining = coverage; remaining != 0; remaining &= remaining - 1) {
                int offset = Long.numberOfTrailingZeros(remaining) * CHUNK_PIXELS;
                for (int i = 0; i < CHUNK_PIXELS; i++) {
                    data.writeInt(pixels[offset + i]);
                }
            }
        }
    }

    public static Version readVersion(InputStream input) throws IOException {
        DataInputStream header = new DataInputStream(input);
        if (header.readInt() != MAGIC) {
            throw new IOException("Unknown surface tile format");
        }
        long revision = header.readLong();
        long coverage = header.readLong();
        if (revision <= 0 || coverage == 0) {
            throw new IOException("Invalid surface tile header");
        }
        return new Version(revision, coverage);
    }

    public static SurfaceTile read(InputStream input) throws IOException {
        Version version = readVersion(input);
        SurfaceTile tile = new SurfaceTile();
        tile.revision = version.revision();
        tile.coverage = version.coverage();
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(new InflaterInputStream(input)))) {
            for (long remaining = tile.coverage; remaining != 0; remaining &= remaining - 1) {
                int offset = Long.numberOfTrailingZeros(remaining) * CHUNK_PIXELS;
                for (int i = 0; i < CHUNK_PIXELS; i++) {
                    tile.pixels[offset + i] = data.readInt();
                }
            }
            if (data.read() != -1) {
                throw new IOException("Unexpected surface tile data");
            }
        }
        return tile;
    }
}
