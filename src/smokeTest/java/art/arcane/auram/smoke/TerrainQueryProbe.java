package art.arcane.auram.smoke;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/** Simulates bounded query-cache hits without caching or changing any result. */
public final class TerrainQueryProbe {
    private static final ThreadLocal<Trace> ACTIVE = new ThreadLocal<>();
    public static void start() { ACTIVE.set(new Trace()); }
    public static void nextAttempt() { ACTIVE.get().within.clear(); }
    public static Trace stop() { Trace trace=ACTIVE.get(); ACTIVE.remove(); return trace; }
    public static void recordSetup() { Trace trace=ACTIVE.get(); if(trace!=null) trace.noiseChunks++; }
    public static void record(Object generator, int x, int z, LevelHeightAccessor height, RandomState state, Object type) {
        Trace trace=ACTIVE.get();
        if(trace==null) return;
        Query key=new Query(generator,state,x,z,height.getMinBuildHeight(),height.getHeight(),type);
        trace.calls++;
        if(!trace.within.add(key)) trace.withinHits++;
        if(trace.small.get(key)!=null) trace.smallHits++;
        if(trace.large.get(key)!=null) trace.largeHits++;
        trace.small.put(key,Boolean.TRUE); trace.large.put(key,Boolean.TRUE);
        if(trace.small.size()>512) trace.small.remove(trace.small.keySet().iterator().next());
        if(trace.large.size()>4096) trace.large.remove(trace.large.keySet().iterator().next());
    }
    public static final class Trace {
        long calls, withinHits, smallHits, largeHits,noiseChunks;
        final Set<Query> within=new HashSet<>();
        final LinkedHashMap<Query,Boolean> small=new LinkedHashMap<>(1024,0.75f,true);
        final LinkedHashMap<Query,Boolean> large=new LinkedHashMap<>(8192,0.75f,true);
        public String json() {
            return "{\"queries\":"+calls+",\"noise_chunks\":"+noiseChunks+",\"within_attempt_duplicates\":"+withinHits
                    +",\"lru_512_hits\":"+smallHits+",\"lru_4096_hits\":"+largeHits+"}";
        }
    }
    private record Query(Object generator,RandomState state,int x,int z,int minY,int height,Object type) {
        @Override public boolean equals(Object other) {
            return other instanceof Query q && generator==q.generator && state==q.state && x==q.x && z==q.z
                    && minY==q.minY && height==q.height && type==q.type;
        }
        @Override public int hashCode() {
            int h=31*System.identityHashCode(generator)+System.identityHashCode(state);
            h=31*h+x;h=31*h+z;h=31*h+minY;h=31*h+height;
            return 31*h+System.identityHashCode(type);
        }
    }
}
