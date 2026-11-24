package art.arcane.auram.vapor.client;

import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.VoxelOccupancy;
import art.arcane.auram.field.WindField;
import art.arcane.auram.field.WindTopology;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static org.lwjgl.opengl.GL43C.*;

/** Render-thread solver. Only sources, terrain changes and small asynchronous reports cross the GL boundary. */
final class VaporCompute implements AutoCloseable {
    private static final int BINDINGS = 8;
    private static final int CELLS = BLOCKS.cells();
    private static final int WEATHER_CELLS = 512;
    private static final int WIND_TAIL_TICKS = 280;
    private static final int REPORT_INTS = 8;
    private static final int[] INITIAL_REPORT = {64, 64, 64, 0, 0, 0, 0, 0};

    record Inputs(VoxelOccupancy occupancy, boolean[] open, WindTopology topology, VaporVolume.Erosion erosion,
                  List<VaporVolume.Source> pulses, List<VaporVolume.Source> emitters,
                  List<WindField.Source> gusts, List<WindField.Source> windEmitters) {}

    private final int[] labels = new int[2];
    private final int[] wind = new int[2];
    private final int[] vapor = new int[2];
    private final Report[] reports = {new Report(), new Report(), new Report()};
    private final FieldProbe probe = FieldProbe.create("compute_submit");
    private final VaporPulse pulse = new VaporPulse();
    private final float[] additions = new float[CELLS];
    private final int[] touched = new int[CELLS];
    private int additionCount;

    private IntBuffer upload = MemoryUtil.memAllocInt(CELLS * 2 + WEATHER_CELLS);
    private Program windProgram;
    private Program vaporProgram;
    private int links;
    private int windInput;
    private int vaporInput;
    private int proposals;
    private int capacity;
    private int labelIndex;
    private int windIndex;
    private int vaporIndex;
    private int windTail;
    private long sequence;
    private Position origin;
    private VoxelOccupancy occupancy;
    private WindTopology topology;
    private VaporVolume.Erosion weather;
    private Bounds stored = Bounds.EMPTY;
    private Bounds bounds = Bounds.EMPTY;
    private Bounds latestReport = Bounds.EMPTY;
    private Position reportOrigin;
    private long reportSequence = -1;
    private long publishedAt;

    static boolean available() {
        if (Boolean.getBoolean("auram.vapor.forceCpu")) return false;
        try {
            return GL.getCapabilities().OpenGL43;
        } catch (IllegalStateException noCurrentContext) {
            return false;
        }
    }

    VaporCompute() {
        try (var state = new GlState()) {
            windProgram = new Program("wind.comp");
            vaporProgram = new Program("vapor.comp");
            for (int i = 0; i < 2; i++) {
                labels[i] = buffer(CELLS * Integer.BYTES, GL_DYNAMIC_DRAW, false);
                wind[i] = buffer(WindTopology.NODES * 4 * Float.BYTES, GL_DYNAMIC_COPY, true);
                vapor[i] = buffer(CELLS * 2 * Float.BYTES, GL_DYNAMIC_COPY, true);
            }
            links = buffer(WindTopology.NODES * Integer.BYTES, GL_DYNAMIC_DRAW, false);
            windInput = buffer(160 * 32, GL_STREAM_DRAW, false);
            vaporInput = buffer((WEATHER_CELLS + 2 * CELLS) * Integer.BYTES, GL_DYNAMIC_DRAW, false);
            proposals = buffer(CELLS * 8 * Float.BYTES, GL_DYNAMIC_COPY, false);
            capacity = buffer(CELLS * Float.BYTES, GL_DYNAMIC_COPY, false);
            for (Report report : reports) report.buffer = buffer(REPORT_INTS * Integer.BYTES, GL_STREAM_READ, false);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    Bounds bounds() {
        return bounds;
    }

    Position origin() {
        return origin;
    }

    long publishedAt() {
        return publishedAt;
    }

    boolean hasVapor() {
        return !bounds.empty();
    }

    void step(Inputs input, int volumeTexture) {
        long start = probe == null ? 0 : System.nanoTime();
        try (var state = new GlState()) {
            readReports();
            sequence++;
            Position nextOrigin = input.occupancy.origin();
            Position previousOrigin = origin == null ? nextOrigin : origin;
            int sx = nextOrigin.x() - previousOrigin.x();
            int sy = nextOrigin.y() - previousOrigin.y();
            int sz = nextOrigin.z() - previousOrigin.z();
            boolean changed = input.occupancy != occupancy || input.topology != topology;
            boolean hasWind = windTail > 0 || !input.windEmitters.isEmpty() || !input.gusts.isEmpty();
            boolean hasSources = !input.emitters.isEmpty() || !input.pulses.isEmpty();
            if (changed) uploadTerrain(input);
            if (hasWind && input.topology != null) {
                advanceWind(input, changed, sx, sy, sz);
            }
            origin = nextOrigin;
            topology = input.topology;
            occupancy = input.occupancy;

            if (reportSequence == sequence - 1 && nextOrigin.equals(reportOrigin)) bounds = latestReport;
            bounds = translate(bounds, -sx, -sy, -sz);
            if (!bounds.empty()) bounds = bounds.expand(BLOCKS.side());

            additionCount = 0;
            if (hasSources) prepareAdditions(input);
            if (hasVapor()) advanceVapor(input, volumeTexture, sx, sy, sz);
            else stored = Bounds.EMPTY;
            if (probe != null && probe.measure(start)) logProbe();
        }
    }

    private void uploadTerrain(Inputs input) {
        labelIndex ^= 1;
        upload.clear();
        if (input.topology != null) input.topology.writeLabels(upload);
        else for (boolean cell : input.open) upload.put(cell ? 0 : -1);
        upload.flip();
        upload(labels[labelIndex], 0, upload);
        if (input.topology != null) {
            upload.clear();
            input.topology.writeDescriptors(upload);
            upload.flip();
            upload(links, 0, upload);
        }
    }

    private void advanceWind(Inputs input, boolean changed, int sx, int sy, int sz) {
        glUseProgram(windProgram.id);
        bind(0, links);
        bind(3, windInput);
        if (changed && topology != null && windTail > 0) {
            bind(1, wind[windIndex]);
            bind(2, wind[windIndex ^ 1]);
            bind(4, labels[labelIndex ^ 1]);
            glUniform3i(windProgram.shift, sx, sy, sz);
            glUniform1i(windProgram.phase, 1);
            dispatchWind(WindTopology.NODES);
            windIndex ^= 1;
        }
        int gusts = packWind(input.topology, input.gusts);
        if (gusts > 0) {
            bind(2, wind[windIndex]);
            glUniform1i(windProgram.phase, 2);
            glUniform1i(windProgram.sourceCount, gusts);
            dispatchWind(gusts);
        }
        int emitters = packWind(input.topology, input.windEmitters);
        if (gusts > 0 || emitters > 0) windTail = WIND_TAIL_TICKS;
        if (windTail == 0) return;
        glUniform1i(windProgram.phase, 0);
        glUniform1i(windProgram.sourceCount, emitters);
        bind(1, wind[windIndex]);
        bind(2, wind[windIndex ^ 1]);
        dispatchWind(WindTopology.NODES);
        windIndex ^= 1;
        if (--windTail == 0) clear(wind[windIndex]);
    }

    private static void dispatchWind(int nodes) {
        glDispatchCompute((nodes + 127) / 128, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private int packWind(WindTopology terrain, List<WindField.Source> sources) {
        if (sources.isEmpty()) return 0;
        Map<Integer, float[]> merged = new TreeMap<>();
        for (var source : sources) {
            int index = terrain.node(source.position());
            if (index < 0) continue;
            float[] vector = merged.computeIfAbsent(index, ignored -> new float[3]);
            vector[0] += source.x();
            vector[1] += source.y();
            vector[2] += source.z();
        }
        upload.clear();
        for (var entry : merged.entrySet()) {
            float[] vector = entry.getValue();
            upload.put(entry.getKey()).put(0).put(0).put(0);
            upload.put(Float.floatToRawIntBits(vector[0])).put(Float.floatToRawIntBits(vector[1]))
                    .put(Float.floatToRawIntBits(vector[2])).put(0);
        }
        upload.flip();
        if (upload.hasRemaining()) upload(windInput, 0, upload);
        return merged.size();
    }

    private void advanceVapor(Inputs input, int volumeTexture, int sx, int sy, int sz) {
        updateWeather(input.erosion);
        Bounds padded = bounds.expand(BLOCKS.side());
        Bounds region = new Bounds(padded.minX() & ~3, padded.minY() & ~3, padded.minZ() & ~3,
                (padded.maxX() + 3) & ~3, (padded.maxY() + 3) & ~3, (padded.maxZ() + 3) & ~3);
        Report report = reserveReport();
        glUseProgram(vaporProgram.id);
        bind(0, labels[labelIndex]);
        bind(1, wind[windIndex]);
        bind(2, vapor[vaporIndex]);
        bind(3, vapor[vaporIndex ^ 1]);
        bind(4, proposals);
        bind(5, capacity);
        bind(6, vaporInput);
        bind(7, report.buffer);
        glUniform1i(vaporProgram.additionCount, additionCount);
        glUniform1i(vaporProgram.hasWind, windTail > 0 ? 1 : 0);
        glUniform3i(vaporProgram.shift, sx, sy, sz);
        glUniform3i(vaporProgram.regionMin, region.minX(), region.minY(), region.minZ());
        glUniform3i(vaporProgram.regionMax, region.maxX(), region.maxY(), region.maxZ());
        glUniform3i(vaporProgram.previousMin, stored.minX(), stored.minY(), stored.minZ());
        glUniform3i(vaporProgram.previousMax, stored.maxX(), stored.maxY(), stored.maxZ());
        if (probe != null) probe.positionOutlet(origin);
        glUniform1i(vaporProgram.outletInside, probe == null ? -1 : probe.inlet());
        glUniform1i(vaporProgram.outletOutside, probe == null ? -1 : probe.outlet());
        glBindImageTexture(0, volumeTexture, 0, true, 0, GL_WRITE_ONLY, GL_RGBA8);
        for (int phase = 0; phase < 3; phase++) {
            glUniform1i(vaporProgram.phase, phase);
            glDispatchCompute((region.maxX() - region.minX()) / 4, (region.maxY() - region.minY()) / 4,
                    (region.maxZ() - region.minZ()) / 4);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        }
        glMemoryBarrier(GL_TEXTURE_FETCH_BARRIER_BIT | GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        vaporIndex ^= 1;
        stored = region;
        publishedAt = System.nanoTime();
        report.origin = origin;
        report.sequence = sequence;
        report.fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    private void prepareAdditions(Inputs input) {
        additionCount = 0;
        for (var source : input.pulses) pulse.visit(source, origin, input.open, this::add);
        for (var source : input.emitters) pulse.visit(source, origin, input.open, this::add);
        if (additionCount == 0) return;
        Arrays.sort(touched, 0, additionCount);
        upload.clear();
        for (int i = 0; i < additionCount; i++) {
            int index = touched[i];
            upload.put(index).put(Float.floatToRawIntBits(additions[index]));
            additions[index] = 0;
            int x = index % BLOCKS.side();
            int y = index / BLOCKS.side() % BLOCKS.side();
            int z = index / (BLOCKS.side() * BLOCKS.side());
            bounds = bounds.include(x, y, z);
        }
        bounds = bounds.expand(BLOCKS.side());
        upload.flip();
        upload(vaporInput, WEATHER_CELLS * Float.BYTES, upload);
    }

    private void add(int index, float density) {
        if (density <= 0) return;
        if (additions[index] == 0) touched[additionCount++] = index;
        additions[index] += density;
    }

    private void updateWeather(VaporVolume.Erosion updated) {
        if (updated == weather) return;
        upload.clear();
        for (int z = 0; z < 8; z++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) upload.put(Float.floatToRawIntBits(updated.at(x * 8, y * 8, z * 8)));
            }
        }
        upload.flip();
        upload(vaporInput, 0, upload);
        weather = updated;
    }

    private void readReports() {
        for (Report report : reports) {
            if (report.fence == 0) continue;
            int status = glClientWaitSync(report.fence, GL_SYNC_FLUSH_COMMANDS_BIT, 0);
            if (status == GL_TIMEOUT_EXPIRED) continue;
            glDeleteSync(report.fence);
            report.fence = 0;
            if (status == GL_WAIT_FAILED) continue;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer data = stack.mallocInt(REPORT_INTS);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, report.buffer);
                glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, data);
                if (probe != null) probe.reportComputeFlux((data.get(6) - data.get(7)) / 16777216.0);
                if (report.sequence <= reportSequence) continue;
                latestReport = data.get(0) >= data.get(3) ? Bounds.EMPTY
                        : new Bounds(data.get(0), data.get(1), data.get(2), data.get(3), data.get(4), data.get(5));
                reportOrigin = report.origin;
                reportSequence = report.sequence;
            }
        }
    }

    private void logProbe() {
        Position position = probe.windPosition();
        int node = position == null || topology == null ? -1 : topology.node(position);
        if (node < 0 || windTail == 0) {
            probe.log(0, 0, 0);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var vector = stack.mallocFloat(4);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, wind[windIndex]);
            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, (long) node * 4 * Float.BYTES, vector);
            probe.log(vector.get(0), vector.get(1), vector.get(2));
        }
    }

    private Report reserveReport() {
        Report selected = reports[0];
        for (Report report : reports) {
            if (report.fence == 0 || report.sequence < selected.sequence) selected = report;
            if (report.fence == 0) break;
        }
        if (selected.fence != 0) {
            glDeleteSync(selected.fence);
            // Replace storage instead of waiting for the previous report.
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, selected.buffer);
            glBufferData(GL_SHADER_STORAGE_BUFFER, REPORT_INTS * Integer.BYTES, GL_STREAM_READ);
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, selected.buffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, INITIAL_REPORT);
        return selected;
    }

    private static Bounds translate(Bounds old, int x, int y, int z) {
        if (old.empty() || (x == 0 && y == 0 && z == 0)) return old;
        int minX = Math.max(0, old.minX() + x), maxX = Math.min(BLOCKS.side(), old.maxX() + x);
        int minY = Math.max(0, old.minY() + y), maxY = Math.min(BLOCKS.side(), old.maxY() + y);
        int minZ = Math.max(0, old.minZ() + z), maxZ = Math.min(BLOCKS.side(), old.maxZ() + z);
        return minX >= maxX || minY >= maxY || minZ >= maxZ ? Bounds.EMPTY
                : new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static int buffer(int bytes, int usage, boolean initialize) {
        int buffer = glGenBuffers();
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, usage);
        if (initialize) clear(buffer);
        return buffer;
    }

    private static void clear(int buffer) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, (ByteBuffer) null);
        glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private static void bind(int index, int buffer) {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, index, buffer);
    }

    private static void upload(int buffer, int offset, IntBuffer data) {
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, offset, data);
    }

    @Override
    public void close() {
        for (Report report : reports) {
            if (report.fence != 0) glDeleteSync(report.fence);
            glDeleteBuffers(report.buffer);
        }
        glDeleteBuffers(labels);
        glDeleteBuffers(wind);
        glDeleteBuffers(vapor);
        glDeleteBuffers(links);
        glDeleteBuffers(windInput);
        glDeleteBuffers(vaporInput);
        glDeleteBuffers(proposals);
        glDeleteBuffers(capacity);
        if (windProgram != null) glDeleteProgram(windProgram.id);
        if (vaporProgram != null) glDeleteProgram(vaporProgram.id);
        MemoryUtil.memFree(upload);
    }

    private static final class Report {
        int buffer;
        long fence;
        long sequence;
        Position origin;
    }

    private static final class Program {
        final int id;
        final int phase;
        final int shift;
        final int sourceCount;
        final int additionCount;
        final int hasWind;
        final int regionMin;
        final int regionMax;
        final int previousMin;
        final int previousMax;
        final int outletInside;
        final int outletOutside;

        Program(String name) {
            id = compile(name);
            phase = glGetUniformLocation(id, "phase");
            shift = glGetUniformLocation(id, "shift");
            sourceCount = glGetUniformLocation(id, "sourceCount");
            additionCount = glGetUniformLocation(id, "additionCount");
            hasWind = glGetUniformLocation(id, "hasWind");
            regionMin = glGetUniformLocation(id, "regionMin");
            regionMax = glGetUniformLocation(id, "regionMax");
            previousMin = glGetUniformLocation(id, "previousMin");
            previousMax = glGetUniformLocation(id, "previousMax");
            outletInside = glGetUniformLocation(id, "outletInside");
            outletOutside = glGetUniformLocation(id, "outletOutside");
        }

        private static int compile(String name) {
            String source;
            try (InputStream input = VaporCompute.class.getResourceAsStream("/assets/auram/shaders/fields/" + name)) {
                if (input == null) throw new IOException("Missing shader resource " + name);
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not load " + name, failure);
            }
            int shader = glCreateShader(GL_COMPUTE_SHADER);
            try {
                glShaderSource(shader, source);
                glCompileShader(shader);
                if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                    throw new IllegalStateException(name + " compile failed: " + glGetShaderInfoLog(shader));
                }
                int program = glCreateProgram();
                glAttachShader(program, shader);
                glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                    String log = glGetProgramInfoLog(program);
                    glDeleteProgram(program);
                    throw new IllegalStateException(name + " link failed: " + log);
                }
                glDetachShader(program, shader);
                return program;
            } finally {
                glDeleteShader(shader);
            }
        }
    }

    private static final class GlState implements AutoCloseable {
        private final int program = glGetInteger(GL_CURRENT_PROGRAM);
        private final int buffer = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        private final int[] bindings = new int[BINDINGS];
        private final long[] offsets = new long[BINDINGS];
        private final long[] sizes = new long[BINDINGS];
        private final int image = glGetIntegeri(GL_IMAGE_BINDING_NAME, 0);
        private final int level = glGetIntegeri(GL_IMAGE_BINDING_LEVEL, 0);
        private final boolean layered = glGetIntegeri(GL_IMAGE_BINDING_LAYERED, 0) != 0;
        private final int layer = glGetIntegeri(GL_IMAGE_BINDING_LAYER, 0);
        private final int access = glGetIntegeri(GL_IMAGE_BINDING_ACCESS, 0);
        private final int format = glGetIntegeri(GL_IMAGE_BINDING_FORMAT, 0);

        GlState() {
            for (int i = 0; i < BINDINGS; i++) {
                bindings[i] = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, i);
                if (bindings[i] != 0) {
                    offsets[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, i);
                    sizes[i] = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, i);
                }
            }
        }

        @Override
        public void close() {
            glUseProgram(program);
            for (int i = 0; i < BINDINGS; i++) {
                if (sizes[i] == 0) bind(i, bindings[i]);
                else glBindBufferRange(GL_SHADER_STORAGE_BUFFER, i, bindings[i], offsets[i], sizes[i]);
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            if (image == 0) glBindImageTexture(0, 0, 0, false, 0, GL_READ_ONLY, GL_RGBA8);
            else glBindImageTexture(0, image, level, layered, layer, access, format);
        }
    }
}
