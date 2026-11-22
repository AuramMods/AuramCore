package art.arcane.auram.smoke;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraftforge.network.ConfigSync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class RingSearchSmoke {
    private static final String EAGER_RINGS = "auram.worldgen.eagerStructureRings";

    private RingSearchSmoke() {}

    static void run(ServerLevel level) throws Exception {
        var source = level.getChunkSource();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).asLookup();
        var biomeSource = source.getGenerator().getBiomeSource();
        String initialMode = System.getProperty(EAGER_RINGS);
        StringBuilder report = new StringBuilder();
        int checked = 0;
        try {
            for (long seed : new long[]{level.getSeed(), 1009L, -104729L, 982451653L}) {
                System.setProperty(EAGER_RINGS, "true");
                var eager = ChunkGeneratorStructureState.createForNormal(source.randomState(), seed, biomeSource, structures);
                var expected = eager.possibleStructureSets().stream()
                        .map(holder -> holder.value().placement())
                        .filter(ConcentricRingsStructurePlacement.class::isInstance)
                        .map(ConcentricRingsStructurePlacement.class::cast)
                        .toList();
                long start = System.nanoTime();
                var eagerLists = expected.stream().map(eager::getRingPositionsFor).toList();
                long eagerMillis = (System.nanoTime() - start) / 1_000_000;
                System.clearProperty(EAGER_RINGS);
                var deferred = ChunkGeneratorStructureState.createForNormal(source.randomState(), seed, biomeSource, structures);
                start = System.nanoTime();
                var nearSpawn = expected.stream().map(placement -> placement.isStructureChunk(deferred, 0, 0)).toList();
                long spawnMillis = (System.nanoTime() - start) / 1_000_000;
                for (int i = 0; i < expected.size(); i++) {
                    var placement = expected.get(i);
                    List<ChunkPos> positions = eagerLists.get(i);
                    require(nearSpawn.get(i) == positions.contains(new ChunkPos(0, 0)), "spawn placement changed");
                    for (ChunkPos pos : positions.subList(0, Math.min(8, positions.size()))) {
                        require(placement.isStructureChunk(deferred, pos.x, pos.z)
                                        == placement.isStructureChunk(eager, pos.x, pos.z), "known ring chunk changed");
                        require(placement.isStructureChunk(deferred, pos.x + 1, pos.z)
                                        == placement.isStructureChunk(eager, pos.x + 1, pos.z), "neighboring chunk changed");
                    }
                    require(positions.equals(deferred.getRingPositionsFor(placement)), "ordered ring list changed at seed " + seed);
                    checked += positions.size();
                }
                report.append("seed=%d placements=%d eager_ms=%d spawn_query_ms=%d%n"
                        .formatted(seed, expected.size(), eagerMillis, spawnMillis));
            }
        } finally {
            if (initialMode == null) System.clearProperty(EAGER_RINGS);
            else System.setProperty(EAGER_RINGS, initialMode);
        }
        require(checked > 0, "fixture loaded no ring placements");
        require(ConfigSync.INSTANCE.syncConfigs(true).isEmpty(), "local Forge config packets were generated");
        require(!ConfigSync.INSTANCE.syncConfigs(false).isEmpty(), "remote Forge config packets were skipped");
        Files.writeString(Path.of("auram-ring-search-pass.txt"), report.append("Checked " + checked + " ring positions and Forge config payloads.\n"));
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
