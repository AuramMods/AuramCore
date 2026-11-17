package art.arcane.auram.util;

import art.arcane.auram.mixin.minecraft.NoiseChunkAccess;
import art.arcane.auram.mixin.minecraft.NoiseGeneratorAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
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
    private final int minY, maxY, width, cellHeight, baseCellY;
    private int selectedY = Integer.MIN_VALUE;
    private boolean closed;

    public static TerrainCell open(NoiseBasedChunkGenerator generator, int x, int z,
                                   LevelHeightAccessor height, RandomState state) {
        var settings = generator.generatorSettings().value().noiseSettings().clampToHeightAccessor(height);
        // Vanilla's partial-cell column-array behavior is not a complete state
        // sampler. Keep that unusual accessor on the original generator path.
        if (settings.height() <= 0 || Math.floorMod(settings.minY(), settings.getCellHeight()) != 0
                || settings.height() % settings.getCellHeight() != 0) return null;
        return new TerrainCell(generator,x,z,state,settings);
    }

    private TerrainCell(NoiseBasedChunkGenerator generator,int x,int z,RandomState state,
                        net.minecraft.world.level.levelgen.NoiseSettings settings) {
        var generatorSettings = generator.generatorSettings().value();
        width = settings.getCellWidth();
        cellHeight = settings.getCellHeight();
        minY = settings.minY();
        maxY = minY + settings.height();
        baseCellY = Math.floorDiv(minY,cellHeight);
        defaultBlock = generatorSettings.defaultBlock();
        noise = new NoiseChunk(1,state,Math.floorDiv(x,width)*width,Math.floorDiv(z,width)*width,
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

    @Override public void close() {
        if (!closed) {
            closed = true;
            noise.stopInterpolation();
        }
    }
}
