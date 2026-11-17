package art.arcane.auram.smoke;

import art.arcane.auram.util.TerrainCell;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class SparseTerrainSmoke {
    public static void run(MinecraftServer server) throws Exception {
        int columns=0,states=0,parallel=0;
        for(var level:server.getAllLevels()) {
            if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) continue;
            for(long seed:new long[]{104729,17,1907}) {
                var state=RandomState.create(generator.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
                for(int anchor:new int[]{-81,-4,0,83}) {
                    try(TerrainCell cell=TerrainCell.open(generator,anchor,-anchor,level,state)) {
                        if(cell==null) throw new AssertionError("Normal terrain did not create a cell");
                        int baseX=Math.floorDiv(anchor,cell.width())*cell.width();
                        int baseZ=Math.floorDiv(-anchor,cell.width())*cell.width();
                        for(int dx=0;dx<cell.width();dx++) for(int dz=0;dz<cell.width();dz++) {
                            int x=baseX+dx,z=baseZ+dz;
                            var reference=generator.getBaseColumn(x,z,level,state);
                            for(int y:new int[]{cell.minY()-1,cell.maxY(),63,-1,0,64,65,-32,-31,-33,
                                    cell.maxY()-1,cell.minY(),cell.minY()+1,7,8,9,63}) {
                                if(cell.state(x,y,z)!=reference.getBlock(y)) throw new AssertionError("Sparse terrain mismatch "+level.dimension().location()+" "+x+","+y+","+z);
                                states++;
                            }
                            columns++;
                        }
                    }
                }
                var workers=Executors.newFixedThreadPool(2);
                try {
                    var tasks=new ArrayList<Callable<Boolean>>();
                    for(int i=0;i<12;i++) {
                        final int x=i*7-31,z=i*3+1;
                        var reference=generator.getBaseColumn(x,z,level,state);
                        tasks.add(()->{
                            try(TerrainCell cell=TerrainCell.open(generator,x,z,level,state)) {
                                for(int y=cell.minY();y<cell.maxY();y+=7) if(cell.state(x,y,z)!=reference.getBlock(y)) return false;
                                return true;
                            }
                        });
                    }
                    for(var result:workers.invokeAll(tasks)) {
                        if(!result.get()) throw new AssertionError("Parallel sparse terrain differs");
                        parallel++;
                    }
                } finally { workers.shutdownNow(); }
            }
        }
        Files.writeString(Path.of("sparse-terrain.json"),"{\"reference_columns\":"+columns+",\"state_comparisons\":"+states+",\"parallel_queries\":"+parallel+"}\n");
    }
}
