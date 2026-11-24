package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class VaporDemonstrators {
    private static final ResourceLocation FURNACE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "furnace");
    private static final ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "fire");
    private static final float FURNACE_RATE = 0.14f;
    private static final float FIRE_RATE = 0.32f;

    private VaporDemonstrators() {
    }

    public static void furnace(ServerLevel level, BlockPos position, BlockState state, boolean cooking) {
        if (!cooking) {
            VaporSources.stop(level, FURNACE, position);
            return;
        }

        BlockPos vent = position.above();
        if (!open(level, vent)) vent = position.relative(state.getValue(AbstractFurnaceBlock.FACING));
        if (open(level, vent)) {
            VaporSources.set(level, position, new VaporNetwork.Emitter(FURNACE, vent, FURNACE_RATE));
        } else {
            VaporSources.stop(level, FURNACE, position);
        }
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState block = level.getBlockState(pos);
        return block.getFluidState().isEmpty() && !block.isCollisionShapeFullBlock(level, pos);
    }

    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before.is(Blocks.FIRE) != after.is(Blocks.FIRE)) {
            if (after.is(Blocks.FIRE)) Vapor.setEmitter(level, FIRE, pos, FIRE_RATE);
            else Vapor.stopEmitter(level, FIRE, pos);
        }
        if (before.getBlock() instanceof AbstractFurnaceBlock && before.getBlock() != after.getBlock()) {
            Vapor.stopEmitter(level, FURNACE, pos);
        }
    }

    @SubscribeEvent
    public static void loadFires(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        if (level.getServer().isSameThread()) readFires(level, chunk);
        else level.getServer().execute(() -> readFires(level, chunk));
    }

    private static void readFires(ServerLevel level, LevelChunk chunk) {
        // Read the provided sections directly. Looking up neighboring chunks in a load event can block promotion.
        var pos = chunk.getPos();
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(Blocks.FIRE))) continue;
            int baseY = (chunk.getMinSection() + sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).is(Blocks.FIRE)) {
                            Vapor.setEmitter(level, FIRE, new BlockPos(pos.getMinBlockX() + x, baseY + y,
                                    pos.getMinBlockZ() + z), FIRE_RATE);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var pos = event.getChunk().getPos();
            VaporSources.stopInChunk(level, FIRE, pos);
            VaporSources.stopInChunk(level, FURNACE, pos);
        }
    }

    @SubscribeEvent
    public static void tnt(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getExplosion().getDirectSourceEntity() instanceof PrimedTnt)) return;
        // Delivery is deferred to the level tick end, after the explosion updates the terrain.
        Vapor.pulse(level, BlockPos.containing(event.getExplosion().getPosition()), 1.0f, 7);
    }
}
