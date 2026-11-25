package art.arcane.auram.vapor;

import art.arcane.auram.Auram;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.FieldNetwork;
import art.arcane.auram.mixin.minecraft.ExplosionRadiusAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Auram.MODID)
public final class VaporDemonstrators {
    private static final ResourceLocation FURNACE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "furnace");
    private static final ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "fire");
    private static final ResourceLocation LAVA_CAULDRON = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "lava_cauldron");
    private static final ResourceLocation NITOR = ResourceLocation.fromNamespaceAndPath(Auram.MODID, "thaumaturge/nitor");
    private static final float FURNACE_RATE = 0.14f;
    private static final BlockEmitter FIRE_EMITTER = new BlockEmitter(FIRE, 0.32f, false);
    private static final BlockEmitter CAMPFIRE_EMITTER = new BlockEmitter(FIRE, 0.65f, false);
    private static final BlockEmitter LAVA_CAULDRON_EMITTER = new BlockEmitter(LAVA_CAULDRON, 0.07f, true);
    private static final BlockEmitter NITOR_EMITTER = new BlockEmitter(NITOR, 0.05f, false);
    private static final float TNT_BLAST_RADIUS = 4.0f;
    private static final int TNT_VAPOR_RADIUS = 7;

    private record BlockEmitter(ResourceLocation id, float rate, boolean ventAbove) {
        void start(Level level, BlockPos anchor) {
            BlockPos vent = ventAbove ? anchor.above() : anchor;
            if (level instanceof ServerLevel server) {
                VaporSources.set(server, anchor, new VaporNetwork.Emitter(id, vent, rate));
            } else {
                Vapor.setLocalEmitter(level, id, anchor, vent, rate);
            }
        }
    }

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

    static boolean open(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        return !VaporObstacles.blocksVapor(level, pos, level.getBlockState(pos));
    }

    public static boolean legacyViewers(ServerLevel level) {
        for (var player : level.players()) {
            if (VaporNetwork.available(player) && !FieldNetwork.supportsLocalSources(player)) return true;
        }
        return false;
    }

    static boolean clientOwned(ServerLevel level, Key key) {
        ResourceLocation provider = key.provider();
        return provider.equals(FIRE) || provider.equals(LAVA_CAULDRON) || provider.equals(NITOR)
                || provider.equals(BotaniaVapor.ENDOFLAME) || TinkersVapor.clientOwned(provider)
                || provider.equals(EnderIoVapor.MACHINE) && level.hasChunkAt(key.anchor())
                && EnderIoVapor.hasVisibleActivity(level.getBlockState(key.anchor()));
    }

    public static void blockChanged(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (level.isClientSide() || level instanceof ServerLevel server && legacyViewers(server)) {
            BlockEmitter previous = blockEmitter(before);
            BlockEmitter current = blockEmitter(after);
            if (previous != current) {
                if (previous != null && (current == null || !previous.id().equals(current.id()))) {
                    Vapor.stopEmitter(level, previous.id(), pos);
                }
                if (current != null) current.start(level, pos);
            }
        }
        if (level.isClientSide() && before.getBlock() != after.getBlock()) {
            ResourceLocation previous = before.getBlock().builtInRegistryHolder().key().location();
            if (previous.getNamespace().equals("botania")
                    && (previous.getPath().equals("endoflame") || previous.getPath().equals("floating_endoflame"))) {
                Vapor.stopEmitter(level, BotaniaVapor.ENDOFLAME, pos);
            }
        }
        if (level instanceof ServerLevel server && before.getBlock() instanceof AbstractFurnaceBlock
                && before.getBlock() != after.getBlock()) {
            Vapor.stopEmitter(server, FURNACE, pos);
        }
    }

    private static BlockEmitter blockEmitter(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return FIRE_EMITTER;
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return CAMPFIRE_EMITTER;
        if (state.is(Blocks.LAVA_CAULDRON)) return LAVA_CAULDRON_EMITTER;
        ResourceLocation block = state.getBlock().builtInRegistryHolder().key().location();
        if (block.getNamespace().equals("thaumaturge") && block.getPath().startsWith("nitor_")) return NITOR_EMITTER;
        return null;
    }

    @SubscribeEvent
    public static void loadEmitters(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level) || !level.isClientSide()
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT).execute(() -> readEmitters(level, chunk));
    }

    @SubscribeEvent
    public static void watchEmitters(ChunkWatchEvent.Watch event) {
        if (VaporNetwork.available(event.getPlayer()) && !FieldNetwork.supportsLocalSources(event.getPlayer())) {
            readEmitters(event.getLevel(), event.getChunk());
        }
    }

    private static void readEmitters(Level level, LevelChunk chunk) {
        // Read the provided sections directly. Looking up neighboring chunks in a load event can block promotion.
        var pos = chunk.getPos();
        if (level.isClientSide() && level.getChunkSource().getChunkNow(pos.x, pos.z) != chunk) return;
        var sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            var section = sections[sectionIndex];
            if (!section.maybeHas(state -> blockEmitter(state) != null)) continue;
            int baseY = (chunk.getMinSection() + sectionIndex) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockEmitter source = blockEmitter(section.getBlockState(x, y, z));
                        if (source != null) {
                            source.start(level, new BlockPos(pos.getMinBlockX() + x, baseY + y, pos.getMinBlockZ() + z));
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
            VaporSources.stopInChunk(level, LAVA_CAULDRON, pos);
            VaporSources.stopInChunk(level, NITOR, pos);
            VaporSources.stopInChunk(level, FURNACE, pos);
        }
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var blast = event.getExplosion();
        var pulse = explosionPulse(BlockPos.containing(blast.getPosition()), ((ExplosionRadiusAccess) blast).auram$getRadius());
        // Delivery is deferred to the level tick end, after the explosion updates the terrain.
        if (pulse != null) VaporSources.pulse(level, pulse);
    }

    static VaporNetwork.Pulse explosionPulse(BlockPos center, float blastRadius) {
        if (!Float.isFinite(blastRadius) || blastRadius <= 0.0f) return null;
        float scale = blastRadius / TNT_BLAST_RADIUS;
        float density = Math.min(1.0f, scale);
        int spread = Math.round(Math.min(Vapor.MAX_RADIUS, TNT_VAPOR_RADIUS * scale));
        return new VaporNetwork.Pulse(center, density, spread);
    }
}
