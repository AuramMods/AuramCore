package art.arcane.auram.smoke;

import art.arcane.auram.util.DeferredChunkDirty;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate test mod; never included in the released Auram jar. Run twice in an isolated server. */
@Mod("auram_patch_smoke")
@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class PatchSmokeMod {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LongSet forbiddenChunkLookups = new LongOpenHashSet();
    private static final ChunkPos SAVED_CHUNK = new ChunkPos(2000, 2000);
    private static final BlockPos TANK_POS = new BlockPos(32008, 81, 32008);
    private static final Path MARKER = Path.of("auram-smoke-phase1.txt");

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        try {
            if (System.getProperty("auram.smoke", "").equals("terminal")) {
                TerminalSyncSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-terminal-pass.txt"), "PASS capability-heavy terminal packets and comparisons\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("selectors")) {
                SelectorSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-selectors-pass.txt"), "PASS selector equivalence and installed function attribution\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("terrainbatch")) {
                TerrainBatchSmoke.run(event.getServer());
                HeightCacheSmoke.run(event.getServer());
                ColumnScopeSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-terrainbatch-pass.txt"), "PASS terrain batching, cache ownership and boundaries\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("sparse")) {
                SparseTerrainSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-sparse-pass.txt"), "PASS sparse/column state equivalence and worker isolation\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("columnscope")) {
                ColumnScopeSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-columnscope-pass.txt"), "PASS column cache lifetime, bound, nested exception and worker isolation\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("wrought")) {
                WroughtColumnsSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-wrought-pass.txt"), "PASS installed Wroughtnaut column workload\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("mappingperf")) {
                MappingScopeSmoke.run();
                MappingPerfSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-mappingperf-pass.txt"), "PASS mapping lifetime checks and warmed terrain workload\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("mappingscope")) {
                MappingScopeSmoke.run();
                Files.writeString(Path.of("auram-mappingscope-pass.txt"), "PASS mapping scope lifetime, bound, visitor and concurrency checks\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("density")) {
                DensitySmoke.run(event.getServer());
                Files.writeString(Path.of("auram-density-pass.txt"), "PASS terrain fingerprints, mutation and parallel checks\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("heightcache")) {
                HeightCacheSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-heightcache-pass.txt"), "PASS height cache key, bound and concurrency checks\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("terrain")) {
                TerrainQuerySmoke.run(event.getServer());
                Files.writeString(Path.of("auram-terrain-pass.txt"), "PASS structure terrain query trace\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("fossil")) {
                FossilSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-fossil-pass.txt"), "PASS fossil search equivalence\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("items")) {
                ItemComparisonSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-items-pass.txt"), "PASS installed terminal comparison workload\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("well")) {
                WellSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-well-pass.txt"), "PASS Malum equivalence and terrain benchmark\n");
                return;
            }
            if (System.getProperty("auram.smoke", "").equals("movement")) {
                MovementSmoke.run(event.getServer());
                Files.writeString(Path.of("auram-movement-pass.txt"), "PASS movement support regression checks\n");
                return;
            }
            ServerLevel level = event.getServer().overworld();
            Method mark = Class.forName("mekanism.common.util.WorldUtils")
                    .getMethod("markChunkDirty", net.minecraft.world.level.Level.class, BlockPos.class);
            if (Files.exists(MARKER)) {
                verifyReloadAndDeferredMark(level, mark);
                Files.writeString(Path.of("auram-smoke-pass.txt"), "PASS: absent chunk guards, loaded Relics event, Mekanism immediate/deferred dirty marking and fluid save/reload\n");
            } else {
                verifyUnloadedHooks(level, mark);
                verifyLoadedQuicksoil(level);
                verifyLoadedRelics(level);
                verifyMekanismSave(level, mark);
                Files.writeString(MARKER, "Saved 1234 mB of water for the second process\n");
            }
            LOGGER.info("[AURAM SMOKE] PASS {}", Files.exists(Path.of("auram-smoke-pass.txt")) ? "phase 2" : "phase 1");
        } catch (Throwable t) {
            LOGGER.error("[AURAM SMOKE] FAILED", t);
            try { Files.writeString(Path.of("auram-smoke-failed.txt"), t.toString()); } catch (Exception ignored) { }
        } finally {
            forbiddenChunkLookups.clear();
            event.getServer().halt(false);
        }
    }

    private static void verifyUnloadedHooks(ServerLevel level, Method mark) throws Exception {
        ChunkPos missing = new ChunkPos(10000, 10000);
        BlockPos pos = missing.getMiddleBlockPosition(82);
        check(!level.getChunkSource().hasChunk(missing.x, missing.z), "test chunk starts unavailable");
        ArmorStand stand = new ArmorStand(level, pos.getX(), pos.getY(), pos.getZ());
        stand.setOnGround(true);
        stand.setDeltaMovement(0.2, 0, 0);
        ObfuscationReflectionHelper.setPrivateValue(Entity.class, stand, Optional.of(pos.below()), "f_285638_");
        Class<?> config = Class.forName("net.zepalesque.redux.config.ReduxConfig");
        Object common = config.getField("COMMON").get(null);
        Object setting = common.getClass().getField("quicksoil_movement_system").get(common);
        check(setting.getClass().getMethod("get").invoke(setting).toString().equals("highlands"), "Highlands handler is enabled");
        forbiddenChunkLookups.add(missing.toLong());
        try {
            Class.forName("net.zepalesque.redux.event.listener.MobListener")
                    .getMethod("movementHandling", LivingEvent.LivingTickEvent.class)
                    .invoke(null, new LivingEvent.LivingTickEvent(stand));
            invokeFluidCollision(stand);
            mark.invoke(null, level, pos);
        } finally {
            forbiddenChunkLookups.clear();
        }
        check(level.getChunkSource().getChunkNow(missing.x, missing.z) == null, "hooks leave unavailable chunk unloaded");
        LOGGER.info("[AURAM SMOKE] Unavailable Aether, Relics and Mekanism hooks made no blocking chunk requests");
    }

    private static void verifyLoadedQuicksoil(ServerLevel level) throws Exception {
        level.getChunk(SAVED_CHUNK.x, SAVED_CHUNK.z);
        BlockPos support = new BlockPos(32008, 81, 32008);
        Block quicksoil = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryBuild("aether", "quicksoil"));
        level.setBlock(support, quicksoil.defaultBlockState(), 16);
        ArmorStand stand = new ArmorStand(level, 32008.5, 82, 32008.5);
        stand.setOnGround(true);
        stand.setDeltaMovement(0.2, 0, 0.1);
        ObfuscationReflectionHelper.setPrivateValue(Entity.class, stand, Optional.of(support), "f_285638_");
        Class.forName("net.zepalesque.redux.event.listener.MobListener")
                .getMethod("movementHandling", LivingEvent.LivingTickEvent.class)
                .invoke(null, new LivingEvent.LivingTickEvent(stand));
        check(Math.abs(stand.getDeltaMovement().x - 0.34) < 1.0E-8
                && Math.abs(stand.getDeltaMovement().z - 0.17) < 1.0E-8, "Highlands quicksoil acceleration is preserved");
        LOGGER.info("[AURAM SMOKE] Loaded Highlands quicksoil retained its 1.7x acceleration");
    }

    private static void verifyLoadedRelics(ServerLevel level) throws Exception {
        // Place one block inside the edge without neighbor notifications; vanilla
        // fluid placement at the edge would itself load the test's neighbor.
        BlockPos water = new BlockPos(32014, 81, 32008);
        level.setBlock(water, Blocks.WATER.defaultBlockState(), 16);
        ArmorStand stand = new ArmorStand(level, 32015.0, 82, 32008.5);
        AtomicInteger fluidEvents = new AtomicInteger();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingEvent.class, e -> {
            if (e.getEntity() == stand && e.getClass().getName().endsWith(".FluidCollisionEvent")) {
                fluidEvents.incrementAndGet();
            }
        });
        // The scan crosses the eastern boundary: loaded water must still produce
        // the Relics event while unavailable neighboring samples cannot load.
        ChunkPos east = new ChunkPos(SAVED_CHUNK.x + 1, SAVED_CHUNK.z);
        check(level.getChunkSource().getChunkNow(east.x, east.z) == null, "neighbor is not FULL");
        forbiddenChunkLookups.add(east.toLong());
        try { invokeFluidCollision(stand); } finally { forbiddenChunkLookups.clear(); }
        check(fluidEvents.get() > 0, "loaded Relics fluid collision event is preserved");
        LOGGER.info("[AURAM SMOKE] Loaded Relics fluid event survived an unavailable chunk boundary");
    }

    private static void invokeFluidCollision(ArmorStand stand) throws Exception {
        Method collision = Arrays.stream(Entity.class.getDeclaredMethods())
                .filter(m -> m.getName().endsWith("$fluidCollision") && m.getReturnType() == Vec3.class)
                .findFirst().orElseThrow();
        collision.setAccessible(true);
        collision.invoke(stand, new Vec3(0, -1, 0));
    }

    private static void verifyMekanismSave(ServerLevel level, Method mark) throws Exception {
        Block tankBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryBuild("mekanism", "basic_fluid_tank"));
        level.setBlock(TANK_POS, tankBlock.defaultBlockState(), 3);
        Object tile = level.getBlockEntity(TANK_POS);
        Object tank = tile.getClass().getField("fluidTank").get(tile);
        tank.getClass().getMethod("setStack", FluidStack.class).invoke(tank, new FluidStack(Fluids.WATER, 1234));
        LevelChunk chunk = level.getChunk(SAVED_CHUNK.x, SAVED_CHUNK.z);
        chunk.setUnsaved(false);
        forbiddenChunkLookups.add(SAVED_CHUNK.toLong());
        try { mark.invoke(null, level, TANK_POS); } finally { forbiddenChunkLookups.clear(); }
        check(chunk.isUnsaved(), "Mekanism dirty notification is retained");
        level.getChunkSource().save(true);
        LOGGER.info("[AURAM SMOKE] Mekanism marked and saved a real fluid tank without a blocking lookup");
    }

    private static void verifyReloadAndDeferredMark(ServerLevel level, Method mark) throws Exception {
        level.getChunkSource().addRegionTicket(TicketType.FORCED, SAVED_CHUNK, 2, SAVED_CHUNK);
        ((ChunkCacheProbe) level.getChunkSource()).auramSmoke$runDistanceManagerUpdates();
        check(level.getChunkSource().hasChunk(SAVED_CHUNK.x, SAVED_CHUNK.z), "FULL ticket exists");
        check(level.getChunkSource().getChunkNow(SAVED_CHUNK.x, SAVED_CHUNK.z) == null, "FULL chunk is still loading");
        forbiddenChunkLookups.add(SAVED_CHUNK.toLong());
        try { mark.invoke(null, level, TANK_POS); } finally { forbiddenChunkLookups.clear(); }
        Field pendingField = DeferredChunkDirty.class.getDeclaredField("PENDING");
        pendingField.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) pendingField.get(null);
        check(pending.containsKey(level), "dirty notification is deferred");
        LevelChunk chunk = level.getChunk(SAVED_CHUNK.x, SAVED_CHUNK.z);
        check(!pending.containsKey(level), "chunk load consumes the deferred notification");
        check(chunk.isUnsaved(), "deferred notification marks the loaded chunk dirty");
        Object tile = level.getBlockEntity(TANK_POS);
        Object tank = tile.getClass().getField("fluidTank").get(tile);
        FluidStack stack = (FluidStack) tank.getClass().getMethod("getFluid").invoke(tank);
        check(stack.getFluid() == Fluids.WATER && stack.getAmount() == 1234, "Mekanism fluid survives a process restart");
        LOGGER.info("[AURAM SMOKE] Deferred marking and Mekanism 1234 mB fluid save/reload passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
