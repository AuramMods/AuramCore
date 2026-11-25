package art.arcane.auram.vapor.client;

import art.arcane.auram.Auram;
import art.arcane.auram.field.ClientFieldSources;
import art.arcane.auram.field.ClientFieldSources.Key;
import art.arcane.auram.field.FieldFrames;
import art.arcane.auram.field.FieldNetwork;
import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.VoxelOccupancy.SectionOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.client.FieldInterestClient;
import art.arcane.auram.light.LightClient;
import art.arcane.auram.vapor.VaporNetwork;
import art.arcane.auram.vapor.VaporObstacles;
import art.arcane.auram.vapor.VaporState;
import art.arcane.auram.wind.WindNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static art.arcane.auram.field.VoxelGrid.SECTION;
import static art.arcane.auram.field.VoxelGrid.SECTIONS;
import static art.arcane.auram.vapor.client.VaporVolume.SIDE;

@Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT)
public final class VaporClient {
    private static final int SECTIONS_PER_SIDE = SIDE / SECTION;
    private static final int SECTION_READS_PER_TICK = 4;
    private static final int MAX_WAITING_PULSES = 32;
    private static final int PULSE_WAIT_TICKS = 40;
    private static final SectionOccupancy EMPTY_SECTION = new SectionOccupancy(new BitSet());

    private static Session session;

    private VaporClient() {
    }

    @Mod.EventBusSubscriber(modid = Auram.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                VaporNetwork.setReceiver(VaporClient::scheduleFrame);
                FieldNetwork.setVaporReceiver(state -> Minecraft.getInstance().execute(() -> receiveState(state)));
                WindNetwork.setReceiver(frame -> Minecraft.getInstance().execute(() -> receiveWind(frame)));
            });
        }
    }

    private static void scheduleFrame(VaporNetwork.Frame frame) {
        Minecraft.getInstance().execute(() -> receive(frame));
    }

    private static void receive(VaporNetwork.Frame frame) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FieldInterestClient.enabled(FieldNetwork.VAPOR)
                || !level.dimension().location().equals(frame.dimension())) return;
        LightClient.setAlembicLights(level, frame.emitters());
        if (frame.emitters().isEmpty() && frame.pulses().isEmpty() && (session == null || session.level != level)) return;
        Session current = sessionFor(level);
        if (!current.emitters.equals(frame.emitters())) {
            current.emitters = frame.emitters();
            current.sourcesChanged = true;
        }
        for (var pulse : frame.pulses()) current.queuePulse(vaporSource(pulse));
    }

    private static void receiveState(VaporState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FieldInterestClient.enabled(FieldNetwork.VAPOR)
                || !level.dimension().location().equals(state.dimension())) return;
        Session current = sessionFor(level);
        if (state.initial()) current.remoteVapor.clear();
        for (var key : state.removed()) current.remoteVapor.remove(key);
        for (var source : state.changed()) current.remoteVapor.put(source.key(), source.emitter());
        if (state.initial() || !state.changed().isEmpty() || !state.removed().isEmpty()) {
            current.sourcesChanged = true;
            LightClient.setAlembicLights(level, List.copyOf(current.remoteVapor.values()));
        }
        for (var pulse : state.pulses()) current.queuePulse(vaporSource(pulse));
    }

    private static void receiveWind(WindNetwork.Frame frame) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FieldInterestClient.enabled(FieldNetwork.VAPOR)
                || !level.dimension().location().equals(frame.dimension())) return;
        if (frame.emitters().isEmpty() && frame.gusts().isEmpty() && (session == null || session.level != level)) return;
        Session current = sessionFor(level);
        if (!current.windEmitters.equals(frame.emitters())) {
            current.windEmitters = frame.emitters();
            current.sourcesChanged = true;
        }
        for (var gust : frame.gusts()) {
            if (!current.queueGust(windSource(gust))) break;
        }
    }

    private static VaporVolume.Source vaporSource(VaporNetwork.Emitter source) {
        return new VaporVolume.Source(position(source.position()), source.rate(), 0);
    }

    private static VaporVolume.Source vaporSource(VaporNetwork.Pulse pulse) {
        return new VaporVolume.Source(position(pulse.position()), pulse.density(), pulse.radius());
    }

    private static WindField.Source windSource(WindNetwork.Flow flow) {
        return new WindField.Source(position(flow.position()), flow.x(), flow.y(), flow.z());
    }

    static Position position(BlockPos pos) {
        return new Position(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean localPulse(Level level, VaporNetwork.Pulse pulse) {
        if (!(level instanceof ClientLevel client) || !FieldInterestClient.enabled(FieldNetwork.VAPOR)
                || Minecraft.getInstance().level != level
                || level.isOutsideBuildHeight(pulse.position()) || !level.hasChunkAt(pulse.position())) return false;
        return sessionFor(client).queuePulse(vaporSource(pulse));
    }

    public static boolean localGust(Level level, WindNetwork.Flow gust) {
        if (!(level instanceof ClientLevel client) || !FieldInterestClient.enabled(FieldNetwork.VAPOR)
                || Minecraft.getInstance().level != level
                || level.isOutsideBuildHeight(gust.position()) || !level.hasChunkAt(gust.position())) return false;
        return sessionFor(client).queueGust(windSource(gust));
    }

    static VaporWorker worker() {
        Session current = session;
        return current == null || current.level != Minecraft.getInstance().level ? null : current.worker;
    }

    static FieldFrames<VaporVolume.Snapshot> frames() {
        Session current = session;
        return current == null || current.level != Minecraft.getInstance().level
                ? FieldFrames.empty() : current.worker.frames();
    }

    private static Session sessionFor(ClientLevel level) {
        if (session == null || session.level != level) {
            reset();
            session = new Session(level);
        }
        return session;
    }

    public static void reset() {
        if (session != null) {
            session.worker.close();
            session = null;
            VaporTexture.releaseWorld();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (session != null && level != session.level) reset();
        var camera = client.getCameraEntity();
        if (level == null || camera == null || !FieldInterestClient.enabled(FieldNetwork.VAPOR)) return;

        boolean paused = client.isPaused();
        BlockPos center = camera.blockPosition();
        VaporAmbient.Sources local = paused || VaporAtmosphere.current() == null
                ? VaporAmbient.Sources.EMPTY : VaporAmbient.sample(level, center);
        Map<Key, VaporNetwork.Emitter> machines = paused || VaporAtmosphere.current() == null ? Map.of()
                : LocalMachineVapor.collect(level, VoxelGrid.originAround(center.getX(), center.getY(), center.getZ()));
        if (session != null || !local.empty() || !machines.isEmpty()
                || ClientFieldSources.VAPOR.has(level) || ClientFieldSources.WIND.has(level)) {
            sessionFor(level).tick(center, paused, local, machines);
            VaporTexture.pumpCompute();
        }
    }

    @SubscribeEvent
    public static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) LocalMachineVapor.unload(level);
        Session current = session;
        if (current != null && event.getLevel() == current.level) reset();
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
            for (var entity : chunk.getBlockEntities().values()) machineAdded(level, entity);
        }
        if (session != null && event.getLevel() == session.level && event.getChunk() instanceof LevelChunk chunk) {
            session.invalidateColumn(chunk, false);
        }
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) LocalMachineVapor.unload(level, event.getChunk().getPos());
        if (session != null && event.getLevel() == session.level && event.getChunk() instanceof LevelChunk chunk) {
            session.invalidateColumn(chunk, true);
        }
    }

    public static void machineAdded(ClientLevel level, BlockEntity entity) {
        if (!entity.isRemoved()) LocalMachineVapor.add(level, entity);
    }

    public static void machineRemoved(ClientLevel level, BlockPos pos) {
        LocalMachineVapor.remove(level, pos);
    }

    public static void blockChanged(ClientLevel level, BlockPos pos) {
        if (session == null || session.level != level) return;
        session.blockChanged(pos);
    }

    private record WaitingPulse(VaporVolume.Source source, int expiresAt) {
    }

    private record WaitingGust(WindField.Source source, int expiresAt) {}

    private static final class Session {
        private final ClientLevel level;
        private final String dimension;
        private final VaporWorker worker = new VaporWorker();
        private final Map<Position, SectionOccupancy> sections = new HashMap<>();
        private final Map<Position, LevelChunk> knownChunks = new LinkedHashMap<>();
        private final List<Position> window = new ArrayList<>();
        private final ArrayDeque<WaitingPulse> pulses = new ArrayDeque<>();
        private final ArrayDeque<WaitingGust> gusts = new ArrayDeque<>();
        private List<WindNetwork.Emitter> windEmitters = List.of();
        private List<VaporNetwork.Emitter> emitters = List.of();
        private final Map<Key, VaporNetwork.Emitter> remoteVapor = new LinkedHashMap<>();
        private final List<WindNetwork.Emitter> ownedWind = new ArrayList<>();
        private final List<VaporNetwork.Emitter> ownedVapor = new ArrayList<>();
        private List<WindNetwork.Emitter> previousWind = List.of();
        private List<VaporNetwork.Emitter> previousVapor = List.of();
        private Map<Key, VaporNetwork.Emitter> previousMachines = Map.of();
        private List<WindField.Source> wind = List.of();
        private List<VaporVolume.Source> vapor = List.of();
        private boolean sourcesChanged;
        private Position origin;
        private VoxelOccupancy input;
        private VaporVolume.Erosion erosion = VaporVolume.Erosion.CLEAR;
        private VaporAtmosphere atmosphere;
        private VaporAtmosphere.Sample weather;
        private boolean refreshWeather = true;
        private boolean changed;
        private int ticks;

        private Session(ClientLevel level) {
            this.level = level;
            this.dimension = level.dimension().location().toString();
        }

        private boolean queuePulse(VaporVolume.Source pulse) {
            if (pulses.size() == MAX_WAITING_PULSES) return false;
            pulses.addLast(new WaitingPulse(pulse, ticks + PULSE_WAIT_TICKS));
            return true;
        }

        private boolean queueGust(WindField.Source gust) {
            if (gusts.size() == MAX_WAITING_PULSES) return false;
            gusts.addLast(new WaitingGust(gust, ticks + PULSE_WAIT_TICKS));
            return true;
        }

        private void tick(BlockPos camera, boolean paused, VaporAmbient.Sources local,
                          Map<Key, VaporNetwork.Emitter> machines) {
            if (paused) {
                worker.submit(input, erosion, List.of(), true, List.of(), List.of(), List.of());
                return;
            }
            ticks++;
            Position next = VoxelGrid.originAround(camera.getX(), camera.getY(), camera.getZ());
            if (!next.equals(origin)) moveWindow(next, camera);
            readSections();
            if (changed) {
                input = new VoxelOccupancy(origin, sections);
                changed = false;
            }
            for (var pulse : local.puffs()) queuePulse(pulse);
            List<VaporVolume.Source> ready = new ArrayList<>();
            var pending = pulses.iterator();
            while (pending.hasNext()) {
                WaitingPulse pulse = pending.next();
                if (ticks - pulse.expiresAt >= 0 || !inWindow(pulse.source.position().section())) {
                    pending.remove();
                } else if (pulseReady(pulse.source)) {
                    ready.add(pulse.source);
                    pending.remove();
                }
            }
            mergeSources(machines);
            List<VaporVolume.Source> continuous = vapor;
            if (!local.burning().isEmpty()) {
                continuous = new ArrayList<>(continuous);
                continuous.addAll(local.burning());
            }
            if (!continuous.isEmpty() || !ready.isEmpty() || worker.frames().current() != null || VaporTexture.computeActive()) updateErosion();
            List<WindField.Source> readyGusts = List.of();
            if (!gusts.isEmpty()) {
                readyGusts = new ArrayList<>();
                var iterator = gusts.iterator();
                while (iterator.hasNext()) {
                    WaitingGust gust = iterator.next();
                    Position section = gust.source.position().section();
                    if (ticks - gust.expiresAt >= 0 || !inWindow(section)) {
                        iterator.remove();
                    } else if (sections.containsKey(section)) {
                        readyGusts.add(gust.source);
                        iterator.remove();
                    }
                }
            }
            worker.submit(input, erosion, continuous, false, ready, wind, readyGusts);
        }

        private void mergeSources(Map<Key, VaporNetwork.Emitter> machines) {
            ownedVapor.clear();
            ownedWind.clear();
            ClientFieldSources.VAPOR.collect(level, origin, ownedVapor);
            ClientFieldSources.WIND.collect(level, origin, ownedWind);
            ownedVapor.addAll(machines.values());
            if (sourcesChanged || !previousVapor.equals(ownedVapor) || !previousMachines.equals(machines)) {
                if (remoteVapor.isEmpty()) {
                    vapor = ClientFieldSources.mergeLegacy(emitters, ownedVapor,
                            source -> new Key(source.id(), source.position()), VaporClient::vaporSource);
                } else {
                    vapor = new ArrayList<>(ownedVapor.size() + remoteVapor.size());
                    for (var source : ownedVapor) vapor.add(vaporSource(source));
                    remoteVapor.forEach((key, source) -> {
                        if (!ClientFieldSources.VAPOR.contains(level, key) && !machines.containsKey(key)) vapor.add(vaporSource(source));
                    });
                }
                previousVapor = List.copyOf(ownedVapor);
                previousMachines = machines;
            }
            if (sourcesChanged || !previousWind.equals(ownedWind)) {
                wind = ClientFieldSources.mergeLegacy(windEmitters, ownedWind,
                        source -> new Key(source.id(), source.flow().position()), source -> windSource(source.flow()));
                previousWind = List.copyOf(ownedWind);
            }
            sourcesChanged = false;
        }

        private void updateErosion() {
            VaporAtmosphere configured = VaporAtmosphere.current();
            if (!refreshWeather && atmosphere == configured && ticks % 5 != 0) return;
            VaporAtmosphere.Sample local = configured == null ? null
                    : configured.sample(dimension, level.getDayTime(), level.getRainLevel(1.0f));
            if (!refreshWeather && atmosphere == configured && Objects.equals(weather, local)) return;

            if (local == null || local.clear()) {
                erosion = VaporVolume.Erosion.CLEAR;
            } else {
                BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
                erosion = VaporVolume.Erosion.sample((x, y, z) -> {
                    int worldX = origin.x() + x;
                    int worldY = origin.y() + y;
                    int worldZ = origin.z() + z;
                    boolean precipitation = false;
                    if (local.rain() > 0.0f && sections.containsKey(new Position(worldX, worldY, worldZ).section())) {
                        // Weather outside applies beneath roofs and underground as well.
                        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                        sample.set(worldX, Math.max(worldY, surface), worldZ);
                        precipitation = level.getBiome(sample).value().hasPrecipitation();
                    }
                    return configured.lingering(local, worldX, worldY, worldZ, precipitation);
                });
            }
            atmosphere = configured;
            weather = local;
            refreshWeather = false;
        }

        private void moveWindow(Position next, BlockPos camera) {
            origin = next;
            refreshWeather = true;
            int x0 = next.x() / SECTION;
            int y0 = next.y() / SECTION;
            int z0 = next.z() / SECTION;
            window.clear();
            for (int x = x0; x < x0 + SECTIONS_PER_SIDE; x++) {
                for (int y = y0; y < y0 + SECTIONS_PER_SIDE; y++) {
                    for (int z = z0; z < z0 + SECTIONS_PER_SIDE; z++) {
                        window.add(new Position(x, y, z));
                    }
                }
            }
            Position center = position(camera).section();
            window.sort(Comparator.comparingInt(pos -> distance(pos, center)));
            sections.keySet().retainAll(window);
            knownChunks.keySet().retainAll(window);
            changed = true;
        }

        private void readSections() {
            int reads = 0;
            for (Position section : window) {
                if (sections.containsKey(section)) continue;
                int index = level.getSectionIndexFromSectionY(section.y());
                if (index < 0 || index >= level.getSectionsCount()) continue;
                LevelChunk chunk = level.getChunkSource().getChunk(section.x(), section.z(), ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection blocks = chunk.getSection(index);
                sections.put(section, blocks.hasOnlyAir() ? EMPTY_SECTION : readSection(section, blocks));
                knownChunks.put(section, chunk);
                changed = true;
                refreshWeather = true;
                if (++reads == SECTION_READS_PER_TICK) return;
            }
        }

        private SectionOccupancy readSection(Position section, LevelChunkSection blocks) {
            BitSet occupied = new BitSet(SECTION * SECTION * SECTION);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int z = 0; z < SECTION; z++) {
                for (int y = 0; y < SECTION; y++) {
                    for (int x = 0; x < SECTION; x++) {
                        var state = blocks.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        pos.set(section.x() * SECTION + x, section.y() * SECTION + y, section.z() * SECTION + z);
                        if (VaporObstacles.blocksVapor(level, pos, state)) {
                            occupied.set(SECTIONS.index(x, y, z));
                        }
                    }
                }
            }
            return new SectionOccupancy(occupied);
        }

        private void invalidateColumn(LevelChunk chunk, boolean unloading) {
            var pos = chunk.getPos();
            for (Position section : window) {
                if (section.x() != pos.x || section.z() != pos.z || (unloading && knownChunks.get(section) != chunk)) continue;
                sections.remove(section);
                knownChunks.remove(section);
                changed = true;
                refreshWeather = true;
            }
        }

        private void blockChanged(BlockPos pos) {
            Position section = position(pos).section();
            SectionOccupancy previous = sections.get(section);
            if (previous == null) return;
            var state = level.getBlockState(pos);
            boolean blocked = VaporObstacles.blocksVapor(level, pos, state);
            var updated = previous.with(pos.getX() & (SECTION - 1), pos.getY() & (SECTION - 1),
                    pos.getZ() & (SECTION - 1), blocked);
            if (previous != updated) {
                sections.put(section, updated);
                changed = true;
            }
        }

        private boolean pulseReady(VaporVolume.Source pulse) {
            var pos = pulse.position();
            int radius = pulse.radius();
            var low = new Position(pos.x() - radius, pos.y() - radius, pos.z() - radius).section();
            var high = new Position(pos.x() + radius, pos.y() + radius, pos.z() + radius).section();
            for (var section : window) {
                if (section.x() >= low.x() && section.x() <= high.x()
                        && section.y() >= low.y() && section.y() <= high.y()
                        && section.z() >= low.z() && section.z() <= high.z()
                        && !sections.containsKey(section)
                        && level.getChunkSource().getChunk(section.x(), section.z(), ChunkStatus.FULL, false) != null
                        && level.getSectionIndexFromSectionY(section.y()) >= 0
                        && level.getSectionIndexFromSectionY(section.y()) < level.getSectionsCount()) return false;
            }
            return sections.containsKey(pos.section());
        }

        private boolean inWindow(Position section) {
            return window.contains(section);
        }
    }

    private static int distance(Position a, Position b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
}
