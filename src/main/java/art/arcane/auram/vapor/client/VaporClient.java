package art.arcane.auram.vapor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.auram.vapor.client.VaporVolume.SECTION;
import static art.arcane.auram.vapor.client.VaporVolume.SIDE;

@Mod.EventBusSubscriber(modid = "auram", value = Dist.CLIENT)
public final class VaporClient {
    private static final int SECTIONS_PER_SIDE = SIDE / SECTION;
    private static final int SECTION_READS_PER_TICK = 4;
    private static final int MAX_WAITING_PULSES = 32;
    private static final int PULSE_WAIT_TICKS = 40;
    private static final VaporVolume.SectionOccupancy EMPTY_SECTION = new VaporVolume.SectionOccupancy(new BitSet());

    private static Session session;

    private VaporClient() {
    }

    // Called only on the client game thread, including after packet scheduling.
    static void submitPulse(ClientLevel level, VaporVolume.Source pulse) {
        Session current = sessionFor(level);
        if (current.pulses.size() < MAX_WAITING_PULSES) current.pulses.addLast(new WaitingPulse(pulse, current.ticks + PULSE_WAIT_TICKS));
    }

    static void submitEmitters(ClientLevel level, List<VaporVolume.Source> emitters) {
        if (emitters.isEmpty() && (session == null || session.level != level)) return;
        sessionFor(level).emitters = List.copyOf(emitters);
    }

    static VaporWorker.Frames frames() {
        return session == null ? VaporWorker.Frames.EMPTY : session.worker.frames();
    }

    private static Session sessionFor(ClientLevel level) {
        if (session == null || session.level != level) {
            reset();
            session = new Session(level);
        }
        return session;
    }

    private static void reset() {
        if (session != null) {
            session.worker.close();
            session = null;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || session == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level != session.level) {
            reset();
            return;
        }
        var camera = client.getCameraEntity();
        if (camera != null) session.tick(camera.blockPosition(), client.isPaused());
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (session != null && event.getLevel() == session.level && event.getChunk() instanceof LevelChunk chunk) {
            session.invalidateColumn(chunk, false);
        }
    }

    @SubscribeEvent
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (session != null && event.getLevel() == session.level && event.getChunk() instanceof LevelChunk chunk) {
            session.invalidateColumn(chunk, true);
        }
    }

    public static void blockChanged(ClientLevel level, BlockPos pos) {
        if (session == null || session.level != level) return;
        session.blockChanged(pos);
    }

    private record WaitingPulse(VaporVolume.Source source, int expiresAt) {
    }

    private static final class Session {
        private final ClientLevel level;
        private final VaporWorker worker = new VaporWorker();
        private final Map<VaporVolume.Position, VaporVolume.SectionOccupancy> sections = new HashMap<>();
        private final Map<VaporVolume.Position, LevelChunk> knownChunks = new LinkedHashMap<>();
        private final List<VaporVolume.Position> window = new ArrayList<>();
        private final ArrayDeque<WaitingPulse> pulses = new ArrayDeque<>();
        private List<VaporVolume.Source> emitters = List.of();
        private VaporVolume.Position origin;
        private VaporVolume.Occupancy input;
        private boolean changed;
        private int ticks;

        private Session(ClientLevel level) {
            this.level = level;
        }

        private void tick(BlockPos camera, boolean paused) {
            if (paused) {
                worker.submit(input, emitters, true, List.of());
                return;
            }
            ticks++;
            VaporVolume.Position next = VaporVolume.Position.originAround(camera.getX(), camera.getY(), camera.getZ());
            if (!next.equals(origin)) moveWindow(next, camera);
            readSections();
            if (changed) {
                input = new VaporVolume.Occupancy(origin, sections);
                changed = false;
            }
            List<VaporVolume.Source> ready = new ArrayList<>();
            var pending = pulses.iterator();
            while (pending.hasNext()) {
                WaitingPulse pulse = pending.next();
                if (ticks - pulse.expiresAt >= 0 || !inWindow(sectionAt(pulse.source.position()))) {
                    pending.remove();
                } else if (pulseReady(pulse.source)) {
                    ready.add(pulse.source);
                    pending.remove();
                }
            }
            worker.submit(input, emitters, false, ready);
        }

        private void moveWindow(VaporVolume.Position next, BlockPos camera) {
            origin = next;
            int x0 = next.x() / SECTION;
            int y0 = next.y() / SECTION;
            int z0 = next.z() / SECTION;
            window.clear();
            for (int x = x0; x < x0 + SECTIONS_PER_SIDE; x++) {
                for (int y = y0; y < y0 + SECTIONS_PER_SIDE; y++) {
                    for (int z = z0; z < z0 + SECTIONS_PER_SIDE; z++) {
                        window.add(new VaporVolume.Position(x, y, z));
                    }
                }
            }
            VaporVolume.Position center = sectionAt(new VaporVolume.Position(camera.getX(), camera.getY(), camera.getZ()));
            window.sort(Comparator.comparingInt(pos -> distance(pos, center)));
            sections.keySet().retainAll(window);
            knownChunks.keySet().retainAll(window);
            changed = true;
        }

        private void readSections() {
            int reads = 0;
            for (VaporVolume.Position section : window) {
                if (sections.containsKey(section)) continue;
                int index = level.getSectionIndexFromSectionY(section.y());
                if (index < 0 || index >= level.getSectionsCount()) continue;
                LevelChunk chunk = level.getChunkSource().getChunk(section.x(), section.z(), ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection blocks = chunk.getSection(index);
                sections.put(section, blocks.hasOnlyAir() ? EMPTY_SECTION : readSection(section, blocks));
                knownChunks.put(section, chunk);
                changed = true;
                if (++reads == SECTION_READS_PER_TICK) return;
            }
        }

        private VaporVolume.SectionOccupancy readSection(VaporVolume.Position section, LevelChunkSection blocks) {
            BitSet occupied = new BitSet(SECTION * SECTION * SECTION);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int z = 0; z < SECTION; z++) {
                for (int y = 0; y < SECTION; y++) {
                    for (int x = 0; x < SECTION; x++) {
                        var state = blocks.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        pos.set(section.x() * SECTION + x, section.y() * SECTION + y, section.z() * SECTION + z);
                        if (!state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos)) {
                            occupied.set(VaporVolume.sectionIndex(x, y, z));
                        }
                    }
                }
            }
            return new VaporVolume.SectionOccupancy(occupied);
        }

        private void invalidateColumn(LevelChunk chunk, boolean unloading) {
            var pos = chunk.getPos();
            for (VaporVolume.Position section : window) {
                if (section.x() != pos.x || section.z() != pos.z || (unloading && knownChunks.get(section) != chunk)) continue;
                sections.remove(section);
                knownChunks.remove(section);
                changed = true;
            }
        }

        private void blockChanged(BlockPos pos) {
            VaporVolume.Position section = sectionAt(new VaporVolume.Position(pos.getX(), pos.getY(), pos.getZ()));
            VaporVolume.SectionOccupancy previous = sections.get(section);
            if (previous == null) return;
            var state = level.getBlockState(pos);
            boolean blocked = !state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos);
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
            var low = sectionAt(new VaporVolume.Position(pos.x() - radius, pos.y() - radius, pos.z() - radius));
            var high = sectionAt(new VaporVolume.Position(pos.x() + radius, pos.y() + radius, pos.z() + radius));
            for (var section : window) {
                if (section.x() >= low.x() && section.x() <= high.x()
                        && section.y() >= low.y() && section.y() <= high.y()
                        && section.z() >= low.z() && section.z() <= high.z()
                        && !sections.containsKey(section)
                        && level.getChunkSource().getChunk(section.x(), section.z(), ChunkStatus.FULL, false) != null
                        && level.getSectionIndexFromSectionY(section.y()) >= 0
                        && level.getSectionIndexFromSectionY(section.y()) < level.getSectionsCount()) return false;
            }
            return sections.containsKey(sectionAt(pos));
        }

        private boolean inWindow(VaporVolume.Position section) {
            return window.contains(section);
        }
    }

    private static VaporVolume.Position sectionAt(VaporVolume.Position pos) {
        return new VaporVolume.Position(Math.floorDiv(pos.x(), SECTION), Math.floorDiv(pos.y(), SECTION),
                Math.floorDiv(pos.z(), SECTION));
    }

    private static int distance(VaporVolume.Position a, VaporVolume.Position b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
}
