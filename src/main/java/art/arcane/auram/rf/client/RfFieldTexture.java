package art.arcane.auram.rf.client;

import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.client.VoxelTextureUpload;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL30C.GL_RGBA32UI;
import static org.lwjgl.opengl.GL30C.GL_RGBA_INTEGER;

/** A render-thread snapshot of the six half edges centered in each block. */
public final class RfFieldTexture {
    public static final String SAMPLER = "auramRfVolume";
    private static final int SIDE = BLOCKS.side();
    private static final Vector3f origin = new Vector3f();
    private static final Vector3f minimum = new Vector3f();
    private static final Vector3f maximum = new Vector3f();
    private static final Vector3f phaseOrigin = new Vector3f();
    private static final RfPresentation presentation = new RfPresentation();
    private static int session = -1;
    private static int texture;
    private static IntBuffer staging;
    private static IntBuffer clockStaging;
    private static Position base;
    private static Bounds bounds = Bounds.EMPTY;
    private static boolean active;

    private RfFieldTexture() {}

    public static void uniforms(UniformHolder uniforms) {
        uniforms.uniform1b(PER_FRAME, "auramRfActive", () -> active);
        uniforms.uniform3f(PER_FRAME, "auramRfOrigin", () -> origin);
        uniforms.uniform3f(PER_FRAME, "auramRfMinimum", () -> minimum);
        uniforms.uniform3f(PER_FRAME, "auramRfMaximum", () -> maximum);
        uniforms.uniform3f(PER_FRAME, "auramRfPhaseOrigin", () -> phaseOrigin);
    }

    public static int texture() {
        if (texture == 0) {
            texture = glGenTextures();
            VoxelTextureUpload.withUnpackState(texture, () -> {
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA32UI, SIDE, SIDE, SIDE + 1, 0,
                        GL_RGBA_INTEGER, GL_UNSIGNED_INT, (ByteBuffer) null);
            });
        }
        return texture;
    }

    public static void prepareFrame() {
        long now = System.nanoTime();
        int currentSession = RfFieldClient.session();
        if (currentSession != session) {
            session = currentSession;
            presentation.reset(now);
            base = null;
        }
        if (texture == 0) {
            active = false;
            return;
        }
        boolean changed = presentation.step(RfFieldClient.samples(), now);
        var tracks = presentation.tracks();
        if (tracks.isEmpty()) {
            active = false;
            base = null;
            return;
        }

        var camera = CameraUniforms.getUnshiftedCameraPosition();
        var nextBase = VoxelGrid.originAround((int) Math.floor(camera.x), (int) Math.floor(camera.y),
                (int) Math.floor(camera.z));
        boolean uploadVolume = changed || !nextBase.equals(base);
        if (uploadVolume) {
            base = nextBase;
            bounds = calculateBounds(tracks);
        }
        if (!bounds.empty()) {
            VoxelTextureUpload.withUnpackState(texture, () -> {
                if (uploadVolume) upload(tracks);
                uploadClocks();
            });
        }
        active = !bounds.empty();
        if (!active) return;
        origin.set(base.x() - camera.x, base.y() - camera.y, base.z() - camera.z);
        minimum.set(bounds.minX(), bounds.minY(), bounds.minZ());
        maximum.set(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        phaseOrigin.set(base.x() & 255, base.y() & 255, base.z() & 255);
    }

    private static Bounds calculateBounds(Collection<RfPresentation.Track> tracks) {
        Bounds region = Bounds.EMPTY;
        for (var track : tracks) {
            BlockPos pos = BlockPos.of(track.edge.position());
            int x = pos.getX() - base.x(), y = pos.getY() - base.y(), z = pos.getZ() - base.z();
            if (BLOCKS.contains(x, y, z)) region = region.include(x, y, z);
            int axis = track.edge.axis();
            x += axis == 0 ? 1 : 0;
            y += axis == 1 ? 1 : 0;
            z += axis == 2 ? 1 : 0;
            if (BLOCKS.contains(x, y, z)) region = region.include(x, y, z);
        }
        return region;
    }

    private static void upload(Collection<RfPresentation.Track> tracks) {
        if (staging == null) staging = BufferUtils.createIntBuffer(BLOCKS.cells() * 4);
        int width = bounds.maxX() - bounds.minX();
        int height = bounds.maxY() - bounds.minY();
        int depth = bounds.maxZ() - bounds.minZ();
        staging.clear();
        staging.limit(width * height * depth * 4);
        MemoryUtil.memSet(MemoryUtil.memAddress(staging), 0, (long) staging.remaining() * Integer.BYTES);

        for (var track : tracks) {
            BlockPos pos = BlockPos.of(track.edge.position());
            int axis = track.edge.axis();
            int x = pos.getX() - base.x(), y = pos.getY() - base.y(), z = pos.getZ() - base.z();
            int rates = track.packedRates();
            int clock = track.clock.slot + 1;
            put(x, y, z, axis, rates, clock, width, height);
            put(x + (axis == 0 ? 1 : 0), y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0),
                    axis, rates << 16, clock, width, height);
        }

        glTexSubImage3D(GL_TEXTURE_3D, 0, bounds.minX(), bounds.minY(), bounds.minZ(),
                width, height, depth, GL_RGBA_INTEGER, GL_UNSIGNED_INT, staging);
    }

    private static void put(int x, int y, int z, int axis, int packed, int clock, int width, int height) {
        if (!BLOCKS.contains(x, y, z)) return;
        int offset = (x - bounds.minX() + width * (y - bounds.minY() + height * (z - bounds.minZ()))) * 4;
        staging.put(offset + axis, staging.get(offset + axis) | packed);
        staging.put(offset + 3, clock);
    }

    /** The extra Z slice stores circuit phases. Upload it separately to avoid rewriting the field every frame. */
    private static void uploadClocks() {
        if (clockStaging == null) clockStaging = BufferUtils.createIntBuffer(SIDE * SIDE * 4);
        var clocks = presentation.clocks();
        int rows = (clocks.size() + SIDE - 1) / SIDE;
        clockStaging.clear();
        clockStaging.limit(rows * SIDE * 4);
        for (var clock : clocks) clockStaging.put(clock.slot * 4, clock.encodedPhase());
        glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, SIDE, SIDE, rows, 1,
                GL_RGBA_INTEGER, GL_UNSIGNED_INT, clockStaging);
    }
}
