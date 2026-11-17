package art.arcane.auram.util;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.LinkedHashMap;

/** Owned by one jigsaw assembly; no world-global or worker-global retention. */
public final class StructureHeightCache {
    private static final int LIMIT = 1024;
    private final LinkedHashMap<Key,Integer> values = new LinkedHashMap<>(128,0.75f,true);
    // The cache belongs to one jigsaw assembly. No terrain session is shared
    // with another assembly; firstFree serializes access to its mutable cells.
    private TerrainBatch terrain;

    public synchronized int firstFree(ChunkGenerator generator, int x, int z, Heightmap.Types type,
                                      LevelHeightAccessor height, RandomState state) {
        Key key=new Key(generator,state,height,x,z,height.getMinBuildHeight(),height.getHeight(),type);
        Integer cached=values.get(key);
        if(cached!=null) return cached;
        int value;
        if(generator.getClass()==NoiseBasedChunkGenerator.class) {
            if(terrain==null || !terrain.matches(generator,height,state)) {
                if(terrain!=null) terrain.close();
                terrain=new TerrainBatch((NoiseBasedChunkGenerator)generator,height,state,false);
            }
            value=terrain.height(x,z,type);
        } else value=generator.getFirstFreeHeight(x,z,type,height,state);
        values.put(key,value);
        if(values.size()>LIMIT) values.remove(values.keySet().iterator().next());
        return value;
    }

    private record Key(ChunkGenerator generator, RandomState state, LevelHeightAccessor accessor,
                       int x, int z, int minY, int height, Heightmap.Types type) {
        @Override public boolean equals(Object other) {
            return other instanceof Key k && generator==k.generator && state==k.state && accessor==k.accessor
                    && x==k.x && z==k.z && minY==k.minY && height==k.height && type==k.type;
        }
        @Override public int hashCode() {
            int h=System.identityHashCode(generator);
            h=31*h+System.identityHashCode(state);h=31*h+System.identityHashCode(accessor);
            h=31*h+x;h=31*h+z;h=31*h+minY;h=31*h+height;
            return 31*h+type.ordinal();
        }
    }
}
