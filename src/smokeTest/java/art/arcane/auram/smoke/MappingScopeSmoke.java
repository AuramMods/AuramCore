package art.arcane.auram.smoke;

import art.arcane.auram.util.NoiseMappingScope;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.function.Function;

public final class MappingScopeSmoke {
    public static void run() throws Exception {
        Object[] completedFrame={null};
        DensityFunction input=DensityFunctions.constant(0.25);
        int[] calls={0},foreignCalls={0};
        DensityFunction.Visitor visitor=f->{calls[0]++;return f;};
        DensityFunction.Visitor foreign=f->{foreignCalls[0]++;return f;};
        DensityFunction root=new FunctionNode(v->{
            completedFrame[0]=v;
            DensityFunction a=NoiseMappingScope.shared(input,v);
            try {
                NoiseMappingScope.map(new FunctionNode(nested->{throw new IllegalStateException("intentional test failure");}),foreign);
                throw new AssertionError("nested failure did not propagate");
            } catch(IllegalStateException expected) { }
            check(a==NoiseMappingScope.shared(input,v),"nested exception restores the parent memoization scope");
            NoiseMappingScope.shared(input,foreign);NoiseMappingScope.shared(input,foreign);
            try {
                Object frame=v;Field mapped=frame.getClass().getDeclaredField("mapped");mapped.setAccessible(true);
                for(int i=0;i<5000;i++) NoiseMappingScope.shared(DensityFunctions.constant(i+0.125),v);
                check(((Map<?,?>)mapped.get(frame)).size()==4096,"mapping scope is bounded at 4096 inputs");
            } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
            return a;
        });
        NoiseMappingScope.map(root,visitor);
        check(calls[0]==5001,"same source branch maps once before the limit");
        check(foreignCalls[0]==2,"different visitors retain their own traversal");
        Field mapped=completedFrame[0].getClass().getDeclaredField("mapped");mapped.setAccessible(true);
        Field active=completedFrame[0].getClass().getDeclaredField("active");active.setAccessible(true);
        check(((Map<?,?>)mapped.get(completedFrame[0])).isEmpty() && !active.getBoolean(completedFrame[0]),
                "completed visitor retains no inputs/results and cannot memoize again");
        NoiseMappingScope.shared(input,visitor);NoiseMappingScope.shared(input,visitor);
        check(calls[0]==5003,"ordinary traversals outside NoiseChunk scopes are unchanged");
        NoiseMappingScope.shared(input,(DensityFunction.Visitor)completedFrame[0]);
        NoiseMappingScope.shared(input,(DensityFunction.Visitor)completedFrame[0]);
        check(calls[0]==5005 && ((Map<?,?>)mapped.get(completedFrame[0])).isEmpty(),
                "a retained completed visitor cannot retain or reuse cached results");
        var workers=Executors.newFixedThreadPool(2);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<24;i++) {
                final int id=i;
                tasks.add(()->{
                    DensityFunction own=NoiseMappingScope.map(new FunctionNode(v->NoiseMappingScope.shared(input,v)),
                            f->DensityFunctions.constant(id+0.5));
                    return own.minValue()==id+0.5;
                });
            }
            for(var result:workers.invokeAll(tasks)) check(result.get(),"mapping frames remain isolated between workers");
        } finally { workers.shutdownNow(); }
    }
    private record FunctionNode(Function<DensityFunction.Visitor,DensityFunction> action) implements DensityFunction.SimpleFunction {
        @Override public DensityFunction mapAll(DensityFunction.Visitor visitor) { return action.apply(visitor); }
        @Override public double compute(DensityFunction.FunctionContext context) { throw new UnsupportedOperationException(); }
        @Override public double minValue() { return 0; }
        @Override public double maxValue() { return 0; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { throw new UnsupportedOperationException(); }
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
