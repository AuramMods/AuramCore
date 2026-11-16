package art.arcane.auram.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

/** Output fingerprints are compared between independently booted reference/candidate jars. */
public final class DensitySmoke {
    public static void run(MinecraftServer server) throws Exception {
        var results=new ArrayList<String>();
        for(var level:server.getAllLevels()) {
            if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) continue;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            var times=new ArrayList<Double>();
            for(long seed:new long[]{104729,17,1907}) {
                RandomState state=RandomState.create(generator.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
                for(int i=0;i<12;i++) {
                    int x=-79+i*17,z=131-i*13;
                    long start=System.nanoTime();
                    var column=generator.getBaseColumn(x,z,level,state);
                    times.add((System.nanoTime()-start)/1e6);
                    for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                        digest.update(column.getBlock(y).toString().getBytes(StandardCharsets.UTF_8));
                    }
                    for(var type:new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG}) {
                        digest.update(Integer.toString(generator.getBaseHeight(x,z,type,level,state)).getBytes(StandardCharsets.UTF_8));
                    }
                    int y=Math.max(generator.generatorSettings().value().noiseSettings().minY(),level.getMinBuildHeight());
                    var original=column.getBlock(y);
                    column.setBlock(y,original.is(Blocks.GOLD_BLOCK)?Blocks.AIR.defaultBlockState():Blocks.GOLD_BLOCK.defaultBlockState());
                    if(generator.getBaseColumn(x,z,level,state).getBlock(y)!=original) throw new AssertionError("Column mutation leaked to another query");
                }
                // Two actual workers, independent NoiseChunks with the same state.
                var workers=Executors.newFixedThreadPool(2);
                try {
                    var tasks=new ArrayList<Callable<String>>();
                    var serial=new ArrayList<String>();
                    for(int i=0;i<8;i++) {
                        final int x=i*23-91,z=i*19+31;
                        Callable<String> task=()->{
                            MessageDigest local=MessageDigest.getInstance("SHA-256");
                            var column=generator.getBaseColumn(x,z,level,state);
                            for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) local.update(column.getBlock(y).toString().getBytes(StandardCharsets.UTF_8));
                            return HexFormat.of().formatHex(local.digest());
                        };
                        tasks.add(task);serial.add(task.call());
                    }
                    var parallel=workers.invokeAll(tasks);
                    for(int i=0;i<parallel.size();i++) {
                        if(!serial.get(i).equals(parallel.get(i).get())) throw new AssertionError("Parallel terrain differs from serial terrain");
                        digest.update(serial.get(i).getBytes(StandardCharsets.UTF_8));
                    }
                } finally { workers.shutdownNow(); }
            }
            MessageDigest chunks=MessageDigest.getInstance("SHA-256");
            BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
            for(ChunkPos location:new ChunkPos[]{new ChunkPos(80,80),new ChunkPos(-82,-81)}) {
                var chunk=level.getChunk(location.x,location.z,ChunkStatus.NOISE);
                for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                    pos.set(location.getMinBlockX()+x,y,location.getMinBlockZ()+z);
                    chunks.update(chunk.getBlockState(pos).toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            Collections.sort(times);
            String result=String.format(java.util.Locale.ROOT,
                    "{\"dimension\":\"%s\",\"seeds\":3,\"column_cases\":36,\"parallel_cases\":24,\"noise_chunks\":2,\"median_column_ms\":%.6f,\"column_height_fingerprint\":\"%s\",\"noise_chunk_fingerprint\":\"%s\"}",
                    level.dimension().location(),times.get(times.size()/2),HexFormat.of().formatHex(digest.digest()),HexFormat.of().formatHex(chunks.digest()));
            results.add(result);
            com.mojang.logging.LogUtils.getLogger().info("[AURAM DENSITY] {}",result);
        }
        Files.writeString(Path.of("density-checks.json"),"{\"results\":["+String.join(",",results)+"]}\n");
    }
}
