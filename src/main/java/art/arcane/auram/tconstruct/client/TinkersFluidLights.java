package art.arcane.auram.tconstruct.client;

import art.arcane.auram.field.VoxelGrid.Position;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import slimeknights.mantle.client.render.ChannelFluids;
import slimeknights.mantle.client.render.FaucetFluid;
import slimeknights.mantle.client.render.FluidCuboid;
import slimeknights.mantle.client.render.FluidRenderer;
import slimeknights.tconstruct.smeltery.block.ChannelBlock;
import slimeknights.tconstruct.smeltery.block.FaucetBlock;
import slimeknights.tconstruct.smeltery.block.controller.ControllerBlock;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ChannelBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.FaucetBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.GaugeBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ITankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.ProxyTankBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.client.screen.module.GuiSmelteryTank;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static art.arcane.auram.field.VoxelGrid.SECTION;

final class TinkersFluidLights {
    private static final int SIDE = BLOCKS.side();
    // The render model uses a 5 mB inset for each end of the heating structure.
    private static final float FLUID_INSET = 0.005f;
    // Reloading the atlas replaces the sprite objects, so old palette entries can be collected.
    private static final Map<TextureAtlasSprite, Integer> SPRITE_COLORS = new WeakHashMap<>();

    private TinkersFluidLights() {}

    static void collect(ClientLevel level, Position base, Int2IntOpenHashMap colors) {
        int firstX = Math.floorDiv(base.x(), SECTION);
        int firstZ = Math.floorDiv(base.z(), SECTION);
        int sections = SIDE / SECTION;
        // Include nearby controllers whose structure extends inside the window.
        for (int x = firstX - 1; x <= firstX + sections; x++) {
            for (int z = firstZ - 1; z <= firstZ + sections; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity.isRemoved()) continue;
                    if (entity instanceof HeatingStructureBlockEntity heating) {
                        collectStructure(heating, base, colors);
                        continue;
                    }
                    BlockPos pos = entity.getBlockPos();
                    if (pos.getX() < base.x() || pos.getX() >= base.x() + SIDE
                            || pos.getZ() < base.z() || pos.getZ() >= base.z() + SIDE || pos.getY() < base.y()) {
                        continue;
                    }
                    boolean inside = pos.getY() < base.y() + SIDE;
                    if (entity instanceof FaucetBlockEntity faucet && faucet.isPouring()) {
                        if (hasFluidModel(faucet)) {
                            collectStream(level, pos, faucet.getBlockState().getValue(FaucetBlock.FACING),
                                    faucet.getRenderFluid(), base, colors);
                        }
                    } else if (entity instanceof ChannelBlockEntity channel) {
                        var state = channel.getBlockState();
                        if (ChannelFluids.REGISTRY.get(state.getBlock()) == null) continue;
                        if (state.getValue(ChannelBlock.DOWN) && channel.isFlowing(Direction.DOWN)) {
                            collectStream(level, pos, Direction.DOWN, channel.getFluid(), base, colors);
                        } else if (inside) {
                            put(pos, channel.getFluid(), 1f, base, colors);
                        }
                    } else if (inside && entity instanceof CastingBlockEntity casting && hasFluidModel(casting)) {
                        var tank = casting.getTank();
                        FluidStack fluid = tank.getFluid();
                        float fill = fill(fluid, tank.getCapacity());
                        if (fluid.getAmount() == tank.getCapacity() && casting.getCoolingTime() > 0) {
                            fill *= Math.max(0f, Math.min(1f,
                                    4f * (1f - (float) casting.getTimer() / casting.getCoolingTime())));
                        }
                        put(pos, fluid, fill, base, colors);
                    } else if (inside && (entity instanceof ITankBlockEntity
                            || entity instanceof GaugeBlockEntity || entity instanceof ProxyTankBlockEntity)) {
                        collectTank(entity, base, colors);
                    }
                }
            }
        }
    }

    private static void collectTank(BlockEntity entity, Position base, Int2IntOpenHashMap colors) {
        if (!hasFluidModel(entity)) return;
        IFluidHandler tank;
        if (entity instanceof ITankBlockEntity container) {
            tank = container.getTank();
        } else if (entity instanceof GaugeBlockEntity gauge) {
            tank = gauge.getTank();
        } else {
            tank = ((ProxyTankBlockEntity) entity).getItemTank();
        }
        if (tank.getTanks() == 0) return;
        FluidStack fluid = tank.getFluidInTank(0);
        put(entity.getBlockPos(), fluid, fill(fluid, tank.getTankCapacity(0)), base, colors);
    }

    private static boolean hasFluidModel(BlockEntity entity) {
        return !FluidCuboid.REGISTRY.get(entity.getBlockState(), List.of()).isEmpty();
    }

    private static void collectStream(ClientLevel level, BlockPos pos, Direction direction, FluidStack fluid,
                                      Position base, Int2IntOpenHashMap colors) {
        if (fluid.isEmpty()) return;
        int color = color(fluid);
        put(pos.getX(), pos.getY(), pos.getZ(), color, 0.6f, base, colors);
        var below = pos.mutable();
        for (int y = pos.getY() - 1; y >= Math.max(base.y(), level.getMinBuildHeight()); y--) {
            var model = FaucetFluid.REGISTRY.get(level.getBlockState(below.setY(y)));
            if (!model.getFluids(direction).isEmpty()) {
                put(pos.getX(), y, pos.getZ(), color, 0.45f, base, colors);
            }
            if (!model.isContinued()) break;
        }
    }

    private static void collectStructure(HeatingStructureBlockEntity heating, Position base,
                                         Int2IntOpenHashMap colors) {
        var structure = heating.getStructure();
        var tank = heating.getTank();
        if (structure == null || !heating.getBlockState().getValue(ControllerBlock.IN_STRUCTURE)
                || tank.getFluids().isEmpty() || tank.getCapacity() <= 0) return;

        BlockPos min = structure.getMinInside();
        BlockPos max = structure.getMaxInside();
        int x0 = Math.max(min.getX(), base.x());
        int x1 = Math.min(max.getX() + 1, base.x() + SIDE);
        int z0 = Math.max(min.getZ(), base.z());
        int z1 = Math.min(max.getZ() + 1, base.z() + SIDE);
        int rows = max.getY() - min.getY() + 1;
        if (x0 >= x1 || z0 >= z1 || rows <= 0 || min.getY() >= base.y() + SIDE || max.getY() < base.y()) return;

        var fluids = tank.getFluids();
        int[] heights = GuiSmelteryTank.calcLiquidHeights(fluids, tank.getCapacity(), rows * 1000 - 10, 100);
        float bottom = FLUID_INSET;
        for (int layer = 0; layer < heights.length; layer++) {
            float top = bottom + heights[layer] / 1000f;
            int color = color(fluids.get(layer));
            int y0 = Math.max(min.getY() + (int) bottom, base.y());
            int y1 = Math.min(min.getY() + (int) Math.ceil(top), base.y() + SIDE);
            for (int y = y0; y < y1; y++) {
                int row = y - min.getY();
                float filled = Math.min(top, row + 1f) - Math.max(bottom, row);
                for (int z = z0; z < z1; z++) {
                    for (int x = x0; x < x1; x++) {
                        put(x, y, z, color, filled, base, colors);
                    }
                }
            }
            bottom = top;
        }
    }

    private static float fill(FluidStack fluid, int capacity) {
        return capacity > 0 ? Math.min(1f, (float) fluid.getAmount() / capacity) : 1f;
    }

    private static void put(BlockPos pos, FluidStack fluid, float strength, Position base, Int2IntOpenHashMap colors) {
        if (!fluid.isEmpty() && strength > 0f) {
            put(pos.getX(), pos.getY(), pos.getZ(), color(fluid), strength, base, colors);
        }
    }

    private static void put(int x, int y, int z, int color, float strength, Position base,
                            Int2IntOpenHashMap colors) {
        x -= base.x();
        y -= base.y();
        z -= base.z();
        if (strength <= 0f || !BLOCKS.contains(x, y, z)) return;
        int intensity = Math.round((color >>> 24) * (float) Math.sqrt(strength));
        int index = BLOCKS.index(x, y, z);
        int previous = colors.get(index);
        // Layers can share a cell. Prefer the dominant one at this grid's resolution.
        if (intensity > (previous >>> 24)) {
            colors.put(index, intensity << 24 | (color & 0xFFFFFF));
        }
    }

    private static int color(FluidStack fluid) {
        var attributes = IClientFluidTypeExtensions.of(fluid.getFluid());
        int tint = attributes.getTintColor(fluid);
        var texture = attributes.getStillTexture(fluid);
        int sprite = texture == null ? 0xFFFFFF
                : SPRITE_COLORS.computeIfAbsent(FluidRenderer.getBlockSprite(texture), TinkersFluidLights::spriteColor);
        return (tint & 0xFF000000)
                | multiplyChannel(sprite, tint, 16) | multiplyChannel(sprite, tint, 8)
                | multiplyChannel(sprite, tint, 0);
    }

    private static int multiplyChannel(int a, int b, int shift) {
        return ((a >> shift & 255) * (b >> shift & 255) / 255) << shift;
    }

    private static int spriteColor(TextureAtlasSprite sprite) {
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int dx = Math.max(1, width / 8);
        int dy = Math.max(1, height / 8);
        int red = 0, green = 0, blue = 0, opacity = 0;
        for (int y = dy / 2; y < height; y += dy) {
            for (int x = dx / 2; x < width; x += dx) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                int alpha = abgr >>> 24;
                red += (abgr & 255) * alpha;
                green += (abgr >> 8 & 255) * alpha;
                blue += (abgr >> 16 & 255) * alpha;
                opacity += alpha;
            }
        }
        return opacity == 0 ? 0xFFFFFF : (red / opacity) << 16 | (green / opacity) << 8 | blue / opacity;
    }
}
