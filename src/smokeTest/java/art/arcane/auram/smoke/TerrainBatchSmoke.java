package art.arcane.auram.smoke;

import art.arcane.auram.util.TerrainBatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class TerrainBatchSmoke {
    public static void run(MinecraftServer server) throws Exception {
        int heights=0,states=0,parallel=0;
        for(var level:server.getAllLevels()) {
            if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) continue;
            var state=level.getChunkSource().randomState();
            for(boolean dense:new boolean[]{false,true}) {
                TerrainBatch batch=new TerrainBatch(generator,level,state,dense);
                try(batch) {
                    for(int anchor:new int[]{-17,-1,0,19}) {
                        for(int dx=0;dx<5;dx++) for(int dz=0;dz<5;dz++) {
                            int x=anchor+dx,z=-anchor+dz;
                            for(var type:Heightmap.Types.values()) {
                                int reference=generator.getBaseHeight(x,z,type,level,state);
                                if(batch.height(x,z,type)!=reference) throw new AssertionError("Batch height mismatch "+level.dimension().location()+" "+x+","+z+" "+type);
                                heights++;
                            }
                            var reference=generator.getBaseColumn(x,z,level,state);
                            var snapshot=batch.column(x,z);
                            for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                                if(snapshot.getBlock(y)!=reference.getBlock(y)) throw new AssertionError("Batch column mismatch "+x+","+y+","+z);
                                states++;
                            }
                            snapshot.setBlock(0,Blocks.GOLD_BLOCK.defaultBlockState());
                            if(batch.state(x,0,z)!=reference.getBlock(0)) throw new AssertionError("Snapshot mutation leaked into terrain batch");
                        }
                    }
                    // More than 16 distinct cells forces eviction and reconstruction.
                    for(int i=0;i<24;i++) batch.height(i*32,-i*32,Heightmap.Types.WORLD_SURFACE_WG);
                    var field=TerrainBatch.class.getDeclaredField("cells");field.setAccessible(true);
                    if(((Map<?,?>)field.get(batch)).size()>16) throw new AssertionError("Unbounded terrain cells");
                    if(batch.height(-17,17,Heightmap.Types.OCEAN_FLOOR_WG)!=generator.getBaseHeight(-17,17,Heightmap.Types.OCEAN_FLOOR_WG,level,state)) throw new AssertionError("Eviction changed terrain");
                }
                try { batch.height(0,0,Heightmap.Types.WORLD_SURFACE_WG); throw new AssertionError("Closed batch reopened"); }
                catch(IllegalStateException expected) { }
            }
            try(TerrainBatch empty=new TerrainBatch(generator,LevelHeightAccessor.create(0,0),state,true)) {
                if(empty.height(3,7,Heightmap.Types.WORLD_SURFACE_WG)!=0) throw new AssertionError("Empty-height fallback differs");
            }
            TerrainBatch.scoped(generator,level,state,true,()->{
                int first=TerrainBatch.queryHeight(generator,7,11,Heightmap.Types.OCEAN_FLOOR_WG,level,state);
                try { TerrainBatch.scoped(generator,level,state,false,()->{throw new IllegalStateException("intentional");}); }
                catch(IllegalStateException expected) { }
                if(first!=TerrainBatch.queryHeight(generator,7,11,Heightmap.Types.OCEAN_FLOOR_WG,level,state)) throw new AssertionError("Nested scope restoration failed");
                return null;
            });
            var workers=Executors.newFixedThreadPool(2);
            try {
                var tasks=new ArrayList<Callable<Boolean>>();
                for(int i=0;i<12;i++) {
                    final int x=i*13-43,z=i*3;
                    int reference=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,state);
                    tasks.add(()->TerrainBatch.scoped(generator,level,state,true,()->
                            TerrainBatch.queryHeight(generator,x,z,Heightmap.Types.WORLD_SURFACE_WG,level,state)==reference));
                }
                for(var task:workers.invokeAll(tasks)) {
                    if(!task.get()) throw new AssertionError("Parallel batch changed terrain");
                    parallel++;
                }
            } finally { workers.shutdownNow(); }
        }
        Files.writeString(Path.of("terrain-batch.json"),"{\"height_comparisons\":"+heights+",\"state_comparisons\":"+states+",\"parallel_queries\":"+parallel+",\"cell_limit\":16}\n");
    }
}
