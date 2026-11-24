package art.arcane.auram.rf.client;

import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.client.VoxelTextureUpload;
import art.arcane.auram.rf.RfFlow;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

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
    private static int texture;
    private static IntBuffer staging;
    private static List<RfFlow.Sample> uploaded = List.of();
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
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA32UI, SIDE, SIDE, SIDE, 0,
                        GL_RGBA_INTEGER, GL_UNSIGNED_INT, (ByteBuffer) null);
            });
        }
        return texture;
    }

    public static void prepareFrame() {
        var samples = RfFieldClient.samples();
        if (texture == 0 || samples.isEmpty()) {
            uploaded = List.of();
            active = false;
            return;
        }

        var camera = CameraUniforms.getUnshiftedCameraPosition();
        var nextBase = VoxelGrid.originAround((int) Math.floor(camera.x), (int) Math.floor(camera.y),
                (int) Math.floor(camera.z));
        if (samples != uploaded || !nextBase.equals(base)) {
            base = nextBase;
            bounds = calculateBounds(samples);
            if (!bounds.empty()) upload(samples);
            uploaded = samples;
        }
        active = !bounds.empty();
        if (!active) return;
        origin.set(base.x() - camera.x, base.y() - camera.y, base.z() - camera.z);
        minimum.set(bounds.minX(), bounds.minY(), bounds.minZ());
        maximum.set(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        phaseOrigin.set(base.x() & 255, base.y() & 255, base.z() & 255);
    }

    private static Bounds calculateBounds(List<RfFlow.Sample> samples) {
        Bounds region = Bounds.EMPTY;
        for (var sample : samples) {
            BlockPos pos = BlockPos.of(sample.edge().position());
            int x = pos.getX() - base.x(), y = pos.getY() - base.y(), z = pos.getZ() - base.z();
            if (BLOCKS.contains(x, y, z)) region = region.include(x, y, z);
            int axis = sample.edge().axis();
            x += axis == 0 ? 1 : 0;
            y += axis == 1 ? 1 : 0;
            z += axis == 2 ? 1 : 0;
            if (BLOCKS.contains(x, y, z)) region = region.include(x, y, z);
        }
        return region;
    }

    private static void upload(List<RfFlow.Sample> samples) {
        if (staging == null) staging = BufferUtils.createIntBuffer(BLOCKS.cells() * 4);
        int width = bounds.maxX() - bounds.minX();
        int height = bounds.maxY() - bounds.minY();
        int depth = bounds.maxZ() - bounds.minZ();
        staging.clear();
        staging.limit(width * height * depth * 4);
        MemoryUtil.memSet(MemoryUtil.memAddress(staging), 0, (long) staging.remaining() * Integer.BYTES);

        for (var sample : samples) {
            BlockPos pos = BlockPos.of(sample.edge().position());
            int axis = sample.edge().axis();
            int x = pos.getX() - base.x(), y = pos.getY() - base.y(), z = pos.getZ() - base.z();
            int rates = encodeRate(sample.forward()) | (encodeRate(sample.backward()) << 8);
            put(x, y, z, axis, rates, width, height);
            put(x + (axis == 0 ? 1 : 0), y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0),
                    axis, rates << 16, width, height);
        }

        VoxelTextureUpload.withUnpackState(texture, () -> glTexSubImage3D(GL_TEXTURE_3D, 0,
                bounds.minX(), bounds.minY(), bounds.minZ(), width, height, depth,
                GL_RGBA_INTEGER, GL_UNSIGNED_INT, staging));
    }

    private static void put(int x, int y, int z, int axis, int packed, int width, int height) {
        if (!BLOCKS.contains(x, y, z)) return;
        int offset = (x - bounds.minX() + width * (y - bounds.minY() + height * (z - bounds.minZ()))) * 4 + axis;
        staging.put(offset, staging.get(offset) | packed);
    }

    /** 31 codes per decade from zero through 100,000,000 RF/t. Bliss controls the visual response. */
    private static int encodeRate(long amount) {
        return (int) Math.round(Math.log10(1.0 + Math.min(amount, 100_000_000L)) * 31.0);
    }
}
