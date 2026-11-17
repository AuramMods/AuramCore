package art.arcane.auram.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.EnumMap;
import java.util.function.Supplier;

/** Bounded interpolation setup shared only inside one structure operation. */
public final class TerrainBatch implements AutoCloseable {
    private static final int LIMIT=16;
    private static final ThreadLocal<TerrainBatch> ACTIVE=new ThreadLocal<>();
    private final NoiseBasedChunkGenerator generator;
    private final LevelHeightAccessor height;
    private final RandomState state;
    private final boolean dense;
    private final int width;
    private final int minBuildHeight,buildHeight;
    private boolean closed;
    private final Long2ObjectLinkedOpenHashMap<Cell> cells=new Long2ObjectLinkedOpenHashMap<>();

    public TerrainBatch(NoiseBasedChunkGenerator generator,LevelHeightAccessor height,RandomState state,boolean dense) {
        this.generator=generator;this.height=height;this.state=state;this.dense=dense;
        width=generator.generatorSettings().value().noiseSettings().getCellWidth();
        minBuildHeight=height.getMinBuildHeight();buildHeight=height.getHeight();
    }

    public boolean matches(ChunkGenerator generator,LevelHeightAccessor height,RandomState state) {
        return !closed && this.generator==generator && this.height==height && this.state==state
                && minBuildHeight==height.getMinBuildHeight() && buildHeight==height.getHeight();
    }

    public static <T> T scoped(ChunkGenerator generator,LevelHeightAccessor height,RandomState state,boolean dense,Supplier<T> action) {
        if(generator.getClass()!=NoiseBasedChunkGenerator.class) return action.get();
        TerrainBatch previous=ACTIVE.get();
        try(TerrainBatch batch=new TerrainBatch((NoiseBasedChunkGenerator)generator,height,state,dense)) {
            ACTIVE.set(batch);
            try { return action.get(); }
            finally { if(previous==null) ACTIVE.remove(); else ACTIVE.set(previous); }
        }
    }

    public static int queryHeight(ChunkGenerator generator,int x,int z,Heightmap.Types type,LevelHeightAccessor height,RandomState state) {
        TerrainBatch batch=ACTIVE.get();
        return batch!=null && batch.matches(generator,height,state)
                ? batch.height(x,z,type) : generator.getBaseHeight(x,z,type,height,state);
    }

    private Cell cell(int x,int z) {
        if(closed) throw new IllegalStateException("Terrain batch is closed");
        if(height.getMinBuildHeight()!=minBuildHeight || height.getHeight()!=buildHeight) return null;
        long key=ChunkPos.asLong(Math.floorDiv(x,width),Math.floorDiv(z,width));
        Cell cached=cells.getAndMoveToLast(key);
        if(cached!=null) return cached;
        if(cells.size()==LIMIT) cells.removeFirst().terrain.close();
        TerrainCell terrain=TerrainCell.open(generator,x,z,height,state);
        if(terrain==null) return null;
        Cell created=new Cell(terrain);
        cells.putAndMoveToLast(key,created);
        return created;
    }

    public int height(int x,int z,Heightmap.Types type) {
        Cell cell=cell(x,z);
        if(cell==null) return generator.getBaseHeight(x,z,type,height,state);
        if(!dense) return cell.terrain.height(x,z,type,height.getMinBuildHeight());
        int[] heights=cell.heights.computeIfAbsent(type,t->cell.terrain.heights(t,height.getMinBuildHeight()));
        return heights[Math.floorMod(x,width)*width+Math.floorMod(z,width)];
    }

    public BlockState state(int x,int y,int z) {
        Cell cell=cell(x,z);
        return cell==null ? generator.getBaseColumn(x,z,height,state).getBlock(y) : cell.terrain.state(x,y,z);
    }

    public NoiseColumn column(int x,int z) {
        Cell cell=cell(x,z);
        return cell==null ? generator.getBaseColumn(x,z,height,state) : cell.terrain.column(x,z);
    }

    @Override public void close() {
        if(closed) return;
        closed=true;
        for(Cell cell:cells.values()) cell.terrain.close();
        cells.clear();
    }

    private static final class Cell {
        final TerrainCell terrain;
        final EnumMap<Heightmap.Types,int[]> heights=new EnumMap<>(Heightmap.Types.class);
        Cell(TerrainCell terrain) { this.terrain=terrain; }
    }
}
