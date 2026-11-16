package art.arcane.auram.util;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

import java.util.IdentityHashMap;
import java.util.function.Function;

/** Shares only transformed DAG branches inside one NoiseChunk mapping pass. */
public final class NoiseMappingScope {
    private static final int LIMIT=4096;
    private NoiseMappingScope() { }

    public static NoiseRouter map(NoiseRouter router,DensityFunction.Visitor visitor) {
        return scoped(visitor,router::mapAll);
    }
    public static DensityFunction map(DensityFunction function,DensityFunction.Visitor visitor) {
        return scoped(visitor,function::mapAll);
    }
    private static <T> T scoped(DensityFunction.Visitor visitor,Function<DensityFunction.Visitor,T> action) {
        Frame frame=new Frame(visitor);
        try { return action.apply(frame); }
        finally {
            // A custom density function could retain its visitor. It must not
            // keep this mapping pass's inputs/results or reuse its memoization.
            frame.active=false;
            frame.mapped.clear();
        }
    }

    public static DensityFunction shared(DensityFunction function,DensityFunction.Visitor visitor) {
        // Only the scoped wrapping visitor can memoize vanilla function nodes.
        // NoiseChunk owns every mapped result in this short scope.
        if(!(visitor instanceof Frame frame) || !frame.active || !function.getClass().getName()
                .startsWith("net.minecraft.world.level.levelgen.DensityFunctions$")) return function.mapAll(visitor);
        DensityFunction cached=frame.mapped.get(function);
        if(cached!=null) return cached;
        DensityFunction result=function.mapAll(visitor);
        if(frame.mapped.size()<LIMIT) frame.mapped.put(function,result);
        return result;
    }
    private static final class Frame implements DensityFunction.Visitor {
        final DensityFunction.Visitor visitor;
        final IdentityHashMap<DensityFunction,DensityFunction> mapped=new IdentityHashMap<>();
        boolean active=true;
        Frame(DensityFunction.Visitor visitor) { this.visitor=visitor; }
        @Override public DensityFunction apply(DensityFunction function) { return visitor.apply(function); }
        @Override public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) { return visitor.visitNoise(noise); }
    }
}
