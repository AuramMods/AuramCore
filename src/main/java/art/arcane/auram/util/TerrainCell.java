package art.arcane.auram.util;

import art.arcane.auram.mixin.minecraft.NoiseChunkAccess;
import art.arcane.auram.mixin.minecraft.NoiseGeneratorAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

/** One owner, one horizontal noise cell, full original height/aquifer context. */
public final class TerrainCell implements AutoCloseable {
    private static final DensityFunctions.BeardifierOrMarker ZERO = new DensityFunctions.BeardifierOrMarker() {
        @Override public double compute(DensityFunction.FunctionContext context) { return 0; }
        @Override public double minValue() { return 0; }
        @Override public double maxValue() { return 0; }
    };
    private final NoiseChunk noise;
    private final BlockState defaultBlock;
    private final int minY, maxY, width, cellHeight, baseCellY, originX, originZ;
    private int selectedY = Integer.MIN_VALUE;
    private boolean closed;

    public static boolean supports(NoiseBasedChunkGenerator generator, LevelHeightAccessor height) {
        return hasCompleteCells(generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(height));
    }

    public static TerrainCell open(NoiseBasedChunkGenerator generator, int x, int z,
                                   LevelHeightAccessor height, RandomState state) {
        var settings = generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(height);
        return hasCompleteCells(settings) ? new TerrainCell(generator,x,z,state,settings) : null;
    }

    private static boolean hasCompleteCells(NoiseSettings settings) {
        // Vanilla's partial-cell column-array behavior is not a complete state
        // sampler. Keep that unusual accessor on the original generator path.
        return settings.height() > 0 && Math.floorMod(settings.minY(), settings.getCellHeight()) == 0
                && settings.height() % settings.getCellHeight() == 0;
    }

    private TerrainCell(NoiseBasedChunkGenerator generator,int x,int z,RandomState state,
                        NoiseSettings settings) {
        var generatorSettings = generator.generatorSettings().value();
        width = settings.getCellWidth();
        cellHeight = settings.getCellHeight();
        minY = settings.minY();
        maxY = minY + settings.height();
        baseCellY = Math.floorDiv(minY,cellHeight);
        originX = Math.floorDiv(x,width)*width;
        originZ = Math.floorDiv(z,width)*width;
        defaultBlock = generatorSettings.defaultBlock();
        noise = new NoiseChunk(1,state,originX,originZ,
                settings,ZERO,generatorSettings,((NoiseGeneratorAccess)generator).auram$fluidPicker().get(),Blender.empty());
        noise.initializeForFirstCellX();
        noise.advanceCellX(0);
    }

    public BlockState state(int x,int y,int z) {
        if (closed) throw new IllegalStateException("Terrain cell is closed");
        if (y < minY || y >= maxY) return Blocks.AIR.defaultBlockState();
        int cellY = Math.floorDiv(y,cellHeight)-baseCellY;
        if (selectedY != cellY) {
            noise.selectCellYZ(cellY,0);
            selectedY = cellY;
        }
        noise.updateForY(y,(double)Math.floorMod(y,cellHeight)/cellHeight);
        noise.updateForX(x,(double)Math.floorMod(x,width)/width);
        noise.updateForZ(z,(double)Math.floorMod(z,width)/width);
        BlockState state = ((NoiseChunkAccess)noise).auram$interpolatedState();
        return state == null ? defaultBlock : state;
    }

    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int width() { return width; }

    public int height(int x,int z,Heightmap.Types type,int fallback) {
        var opaque=type.isOpaque();
        for(int y=maxY-1;y>=minY;y--) if(opaque.test(state(x,y,z))) return y+1;
        return fallback;
    }

    /** A block at the cutoff already proves the surface is strictly above it. */
    public boolean heightExceeds(int x,int z,Heightmap.Types type,int cutoff,int fallback) {
        if(closed) throw new IllegalStateException("Terrain cell is closed");
        if(fallback>cutoff) return true;
        int first=Math.max(minY,cutoff);
        if(first>=maxY) return false;
        var opaque=type.isOpaque();
        if(opaque.test(state(x,first,z))) return true;
        // Air at the cutoff does not disprove an overhang/floating island.
        // Search the entire remaining upper interval before rejecting.
        for(int y=maxY-1;y>first;y--) if(opaque.test(state(x,y,z))) return true;
        return false;
    }

    /** Dense callers share each interpolated vertical cell across every X/Z. */
    public int[] heights(Heightmap.Types type,int fallback) {
        int[] values=new int[width*width];
        java.util.Arrays.fill(values,fallback);
        boolean[] found=new boolean[values.length];
        int remaining=values.length;
        var opaque=type.isOpaque();
        for(int y=maxY-1;y>=minY && remaining>0;y--) {
            for(int dx=0;dx<width;dx++) for(int dz=0;dz<width;dz++) {
                int index=dx*width+dz;
                if(!found[index] && opaque.test(state(originX+dx,y,originZ+dz))) {
                    found[index]=true;values[index]=y+1;remaining--;
                }
            }
        }
        return values;
    }

    public NoiseColumn column(int x,int z) {
        BlockState[] values=new BlockState[maxY-minY];
        for(int y=maxY-1;y>=minY;y--) values[y-minY]=state(x,y,z);
        return new NoiseColumn(minY,values);
    }

    @Override public void close() {
        if (!closed) {
            closed = true;
            noise.stopInterpolation();
        }
    }
}
