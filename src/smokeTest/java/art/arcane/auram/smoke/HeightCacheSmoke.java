package art.arcane.auram.smoke;

import art.arcane.auram.util.StructureHeightCache;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.concurrent.Callable;

public final class HeightCacheSmoke {
    public static void run(MinecraftServer server) throws Exception {
        StructureHeightCache cache=new StructureHeightCache();
        int cases=0;
        for(var level:server.getAllLevels()) {
            if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) continue;
            for(long seed:new long[]{104729,17,1907}) {
                RandomState state=RandomState.create(generator.generatorSettings().value(),level.registryAccess().lookupOrThrow(Registries.NOISE),seed);
                for(LevelHeightAccessor height:new LevelHeightAccessor[]{level,LevelHeightAccessor.create(-32,64),LevelHeightAccessor.create(0,0)}) {
                    for(Heightmap.Types type:new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG}) {
                        int expected=generator.getFirstFreeHeight(-79,131,type,height,state);
                        TerrainQueryProbe.start();
                        check(cache.firstFree(generator,-79,131,type,height,state)==expected,"query result matches generator");
                        check(cache.firstFree(generator,-79,131,type,height,state)==expected,"cache hit matches generator");
                        check(TerrainQueryProbe.stop().calls==1,"one synthesis per complete context key");
                        cases++;
                    }
                }
            }
        }
        var generator=server.getLevel(net.minecraft.world.level.Level.NETHER).getChunkSource().getGenerator();
        var state=server.getLevel(net.minecraft.world.level.Level.NETHER).getChunkSource().randomState();
        LevelHeightAccessor empty=LevelHeightAccessor.create(0,0);
        for(int i=0;i<1100;i++) cache.firstFree(generator,i,i,Heightmap.Types.WORLD_SURFACE_WG,empty,state);
        var field=StructureHeightCache.class.getDeclaredField("values"); field.setAccessible(true);
        check(((Map<?,?>)field.get(cache)).size()==1024,"cache remains bounded after eviction");
        var workers=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<24;i++) {
                final int x=i;
                tasks.add(()->cache.firstFree(generator,x,19,Heightmap.Types.OCEAN_FLOOR_WG,empty,state)
                        ==generator.getFirstFreeHeight(x,19,Heightmap.Types.OCEAN_FLOOR_WG,empty,state));
            }
            for(var result:workers.invokeAll(tasks)) check(result.get(),"concurrent queries remain isolated and correct");
        } finally { workers.shutdownNow(); }
        java.nio.file.Files.writeString(java.nio.file.Path.of("height-cache-checks.json"),"{\"context_cases\":"+cases+",\"eviction_queries\":1100,\"concurrent_cases\":24}\n");
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
}
