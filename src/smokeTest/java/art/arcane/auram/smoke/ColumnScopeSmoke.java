package art.arcane.auram.smoke;

import art.arcane.auram.util.StructureColumns;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class ColumnScopeSmoke {
    public static void run(MinecraftServer server) throws Exception {
        var level=server.getLevel(Level.NETHER);
        var generator=(NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
        var state=level.getChunkSource().randomState();
        Field active=StructureColumns.class.getDeclaredField("ACTIVE");active.setAccessible(true);
        ThreadLocal<?> local=(ThreadLocal<?>)active.get(null);
        Object[] completed={null};
        ColumnProbe.start();
        StructureColumns.search(generator,level,state,()->{
            completed[0]=local.get();
            var first=StructureColumns.column(generator,-79,131,level,state);
            check(first==StructureColumns.column(generator,-79,131,level,state),"same search reuses the read-only column");
            try {
                StructureColumns.search(generator,level,state,()->{throw new IllegalStateException("intentional");});
            } catch(IllegalStateException expected) { }
            check(first==StructureColumns.column(generator,-79,131,level,state),"nested failure restores outer search");
            for(int x=0;x<1100;x++) StructureColumns.column(generator,x,13,level,state);
            try {
                Field columns=completed[0].getClass().getDeclaredField("columns");columns.setAccessible(true);
                check(((Map<?,?>)columns.get(completed[0])).size()==1024,"column cache is bounded");
            } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
            return null;
        });
        check(ColumnProbe.stop()==0 && ColumnProbe.lastNoise()>0 && ColumnProbe.lastNoise()<300,
                "unique columns share noise-cell setup and hits avoid synthesis");
        Field columns=completed[0].getClass().getDeclaredField("columns");columns.setAccessible(true);
        check(((Map<?,?>)columns.get(completed[0])).isEmpty() && local.get()==null,"completed search releases columns and thread state");
        ColumnProbe.start();
        StructureColumns.column(generator,-79,131,level,state);
        StructureColumns.column(generator,-79,131,level,state);
        check(ColumnProbe.stop()==2,"queries outside the search are uncached");
        var workers=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<16;i++) {
                final int x=i;
                tasks.add(()->StructureColumns.search(generator,level,state,()->{
                    ColumnProbe.start();
                    var a=StructureColumns.column(generator,x,23,level,state);
                    var b=StructureColumns.column(generator,x,23,level,state);
                    return a==b && ColumnProbe.stop()==0 && ColumnProbe.lastNoise()==1;
                }));
            }
            for(var result:workers.invokeAll(tasks)) check(result.get(),"each worker owns its own search cache");
        } finally { workers.shutdownNow(); }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
