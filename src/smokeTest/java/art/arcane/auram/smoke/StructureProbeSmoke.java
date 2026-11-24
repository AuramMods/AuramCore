package art.arcane.auram.smoke;

import art.arcane.auram.util.TerrainBatch;
import art.arcane.auram.util.TerrainCell;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class StructureProbeSmoke {
    public static void run(MinecraftServer server) throws Exception {
        long comparisons=0;int positive=0,negative=0,parallel=0;
        for(var dimension:List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
            var level=server.getLevel(dimension);
            var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
            for(long seed:new long[]{104729,17,1907}) {
                var state=RandomState.create(generator.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
                for(int i=0;i<24;i++) {
                    int x=(i-12)*17,z=(i%9-5)*19;
                    try(TerrainCell cell=TerrainCell.open(generator,x,z,level,state)) {
                        check(cell!=null,"aligned vanilla generator supported");
                        for(var type:new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG,
                                Heightmap.Types.MOTION_BLOCKING,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}) {
                            int height=generator.getBaseHeight(x,z,type,level,state);
                            for(int cutoff:new int[]{Integer.MIN_VALUE,cell.minY()-1,cell.minY(),-1,0,63,64,65,
                                    height-1,height,cell.maxY()-1,cell.maxY(),Integer.MAX_VALUE}) {
                                boolean reference=height>cutoff;
                                check(cell.heightExceeds(x,z,type,cutoff,level.getMinBuildHeight())==reference,
                                        "threshold matches full height "+dimension+" "+type+" "+cutoff);
                                if(reference)positive++;else negative++;comparisons++;
                            }
                        }
                    }
                }
                List<CompletableFuture<Void>> tasks=new ArrayList<>();
                for(int i=0;i<8;i++) {
                    int x=16*i-67,z=11*i-39;
                    boolean expected=generator.getBaseHeight(x,z,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,level,state)>64;
                    tasks.add(CompletableFuture.runAsync(()-> {
                        try(TerrainCell cell=TerrainCell.open(generator,x,z,level,state)) {
                            check(cell.heightExceeds(x,z,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,64,level.getMinBuildHeight())==expected,
                                    "worker-local threshold result");
                        }
                    }));
                }
                for(var task:tasks) {task.join();parallel++;}
            }
        }
        var level=server.overworld();var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
        TerrainCell closed=TerrainCell.open(generator,0,0,level,level.getChunkSource().randomState());closed.close();
        try {closed.heightExceeds(0,0,Heightmap.Types.WORLD_SURFACE_WG,Integer.MAX_VALUE,level.getMinBuildHeight());
            throw new AssertionError("closed threshold query accepted");} catch(IllegalStateException expected) { }
        check(positive>0 && negative>0,"acceptance and rejection cases covered");
        Map<String,Object> benchmark=benchmarkMushroom(server);
        Files.writeString(Path.of("structure-probe-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("threshold_comparisons",comparisons,"positive",positive,"negative",negative,"parallel_queries",parallel,
                        "closed_scope_checked",true,"nyctophobia_benchmark",benchmark))+"\n");
    }
    public static Map<String,Object> benchmarkMushroom(MinecraftServer server) throws Exception {
        var level=server.overworld();var generator=level.getChunkSource().getGenerator();
        var installed=Class.forName("net.finallion.nyctophobia.world.structures.MushroomStructure")
                .getDeclaredMethod("extraSpawningChecks",Structure.GenerationContext.class);
        installed.setAccessible(true);
        var reference=StructureProbeSmoke.class.getDeclaredMethod("originalMushroomCheck",Structure.GenerationContext.class);
        reference.setAccessible(true);
        List<Structure.GenerationContext> contexts=new ArrayList<>();
        for(int i=0;i<48;i++) contexts.add(new Structure.GenerationContext(level.registryAccess(),generator,
                generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),
                new ChunkPos((i-24)*11,(i%7-5)*13),level,biome->true));
        for(int i=0;i<96;i++) contexts.add(new Structure.GenerationContext(level.registryAccess(),generator,
                generator.getBiomeSource(),level.getChunkSource().randomState(),server.getStructureManager(),level.getSeed(),
                new ChunkPos(80+i,80),level,biome->true));
        int boundaryCases=0;
        for(var context:contexts) if(generator.getFirstOccupiedHeight(context.chunkPos().getMinBlockX(),context.chunkPos().getMinBlockZ(),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,level,context.randomState())==64) boundaryCases++;
        check(boundaryCases>0,"occupied-height-64 regression cases covered");
        double[] oldTimes=new double[25],newTimes=new double[25];
        for(int batch=-5;batch<25;batch++) {
            double oldTime=0,newTime=0;
            for(int i=0;i<contexts.size();i++) {
                boolean[] values=new boolean[2];
                for(int order=0;order<2;order++) {
                    boolean original=(batch+i+order)%2==0;
                    long start=System.nanoTime();
                    boolean result=(boolean)(original?reference:installed).invoke(null,contexts.get(i));
                    double ms=(System.nanoTime()-start)/1e6;
                    values[original?0:1]=result;
                    if(original)oldTime+=ms;else newTime+=ms;
                }
                check(values[0]==values[1],"installed Nyctophobia method matches original predicate");
            }
            if(batch>=0) {oldTimes[batch]=oldTime;newTimes[batch]=newTime;}
        }
        double[] a=oldTimes.clone(),b=newTimes.clone();Arrays.sort(a);Arrays.sort(b);
        Map<String,Object> result=new LinkedHashMap<>(Map.of("cases_per_batch",contexts.size(),"measured_batches",25,
                "occupied_height_64_cases",boundaryCases,"original_median_batch_ms",a[12],"patched_median_batch_ms",b[12],
                "original_batch_ms",oldTimes,"patched_batch_ms",newTimes));
        if (Boolean.getBoolean("auram.smoke.mushroomSharedCell")) {
            long setups = 0;
            for (var context : contexts) {
                boolean expected = originalMushroomCheck(context);
                setups += TerrainBatch.scoped(generator, level, context.randomState(), false, () -> {
                    int x = context.chunkPos().getMinBlockX();
                    int z = context.chunkPos().getMinBlockZ();
                    var batch = TerrainBatch.current(generator, level, context.randomState());
                    check(batch != null && batch.borrowCell(x, z) != null, "fixture creates an existing terrain cell");
                    TerrainQueryProbe.Trace trace;
                    TerrainQueryProbe.start();
                    try {
                        check((boolean) installed.invoke(null, context) == expected, "scoped Nyctophobia predicate");
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    } finally {
                        trace = TerrainQueryProbe.stop();
                    }
                    return trace.noiseChunks;
                });
            }
            check(setups == 0, "mushroom checks reuse the existing sampler");
            result.put("shared_cell_cases", contexts.size());
            result.put("shared_cell_new_samplers", setups);
        }
        Files.writeString(Path.of("mushroom-benchmark.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        return result;
    }
    private static boolean originalMushroomCheck(Structure.GenerationContext context) {
        return context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(),context.chunkPos().getMinBlockZ(),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,context.heightAccessor(),context.randomState())>64;
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
