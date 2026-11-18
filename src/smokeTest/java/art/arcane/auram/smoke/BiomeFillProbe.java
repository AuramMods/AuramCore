package art.arcane.auram.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Probe allocation/counters exist only for semantic checks, not timed batches. */
public final class BiomeFillProbe {
    private static final ThreadLocal<Stats> ACTIVE = new ThreadLocal<>();

    public static final class Stats {
        public long reads;
        public final List<Map<String, Object>> refreshes = new ArrayList<>();
    }

    public static void start() { ACTIVE.set(new Stats()); }
    public static Stats stop() { Stats stats = ACTIVE.get(); ACTIVE.remove(); return stats; }

    public static BiomeResolver wrap(BiomeResolver original) {
        Stats stats = ACTIVE.get();
        if (stats == null) return original;
        return (x, y, z, sampler) -> { stats.reads++; return original.getNoiseBiome(x, y, z, sampler); };
    }

    public static void refresh(List<ChunkAccess> chunks) {
        Stats stats = ACTIVE.get();
        if (stats == null || chunks.isEmpty()) return;
        List<LevelChunk> actual = chunks.stream().map(c -> (LevelChunk) c).toList();
        ServerLevel level = (ServerLevel) actual.get(0).getLevel();
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundChunksBiomesPacket.forChunks(actual).write(encoded);
            var decoded = new ClientboundChunksBiomesPacket(encoded);
            List<ChunkAccess> copies = new ArrayList<>();
            for (var data : decoded.chunkBiomeData()) {
                LevelChunk copy = new LevelChunk(level, data.pos());
                FriendlyByteBuf input = data.getReadBuffer();
                try {
                    copy.replaceBiomes(input);
                    check(input.readableBytes() == 0, "biome packet consumed exactly");
                } finally { input.release(); }
                copies.add(copy);
            }
            String digest = digest(level, chunks);
            check(digest.equals(digest(level, copies)), "client biome payload matches server biome values");
            stats.refreshes.add(Map.of("chunks", chunks.stream().map(c -> List.of(c.getPos().x, c.getPos().z)).toList(),
                    "all_dirty", chunks.stream().allMatch(ChunkAccess::isUnsaved), "decoded_biomes", digest));
        } finally { encoded.release(); }
    }

    public static String digest(ServerLevel level, List<? extends ChunkAccess> chunks) {
        try {
            Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            ByteBuffer word = ByteBuffer.allocate(4);
            for (ChunkAccess chunk : chunks) {
                put(hash, word, chunk.getPos().x); put(hash, word, chunk.getPos().z);
                for (int y = chunk.getMinBuildHeight() >> 2; y < chunk.getMaxBuildHeight() >> 2; y++) {
                    for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                        put(hash, word, registry.getId(chunk.getNoiseBiome((chunk.getPos().x << 2) + x, y,
                                (chunk.getPos().z << 2) + z).value()));
                    }
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void put(MessageDigest hash, ByteBuffer word, int value) {
        word.clear(); word.putInt(value); hash.update(word.array());
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
