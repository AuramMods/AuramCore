package art.arcane.auram.vapor.client;

import art.arcane.auram.field.FieldFrames;
import art.arcane.auram.field.FieldTicker;
import art.arcane.auram.field.client.VoxelTextureUpload;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;

/** Render-thread upload and bindings for the Vapor field. */
public final class VaporTexture {
    public static final String SAMPLER = "auramVaporVolume";
    private static final int SIDE = VaporVolume.SIDE;
    private static final double TICK_NANOS = FieldTicker.TICK_NANOS;
    private static int texture;
    private static ByteBuffer staging;
    private static FieldFrames<VaporVolume.Snapshot> uploaded = FieldFrames.empty();
    private static Bounds bounds = Bounds.EMPTY;
    private static final Vector3f origin = new Vector3f();
    private static final Vector3f center = new Vector3f();
    private static final Vector3f minimum = new Vector3f();
    private static final Vector3f maximum = new Vector3f();
    private static double blend;
    private static boolean active;
    private static VaporCompute compute;

    private VaporTexture() {
    }

    public static void uniforms(UniformHolder uniforms) {
        uniforms.uniform1b(PER_FRAME, "auramVaporActive", () -> active);
        uniforms.uniform1f(PER_FRAME, "auramVaporBlend", () -> blend);
        uniforms.uniform3f(PER_FRAME, "auramVaporOrigin", () -> origin);
        uniforms.uniform3f(PER_FRAME, "auramVaporCenter", () -> center);
        uniforms.uniform3f(PER_FRAME, "auramVaporMinimum", () -> minimum);
        uniforms.uniform3f(PER_FRAME, "auramVaporMaximum", () -> maximum);
    }

    public static int texture() {
        if (texture == 0) {
            texture = glGenTextures();
            staging = BufferUtils.createByteBuffer(VaporVolume.CELLS * 4);
            VoxelTextureUpload.withUnpackState(texture, () -> {
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA8, SIDE, SIDE, SIDE, 0, GL_RGBA, GL_UNSIGNED_BYTE, staging);
            });
        }
        return texture;
    }

    static boolean computeActive() {
        return compute != null && compute.hasVapor();
    }

    static void releaseWorld() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(VaporTexture::releaseWorld);
            return;
        }
        if (compute != null) {
            compute.close();
            compute = null;
        }
        uploaded = FieldFrames.empty();
        bounds = Bounds.EMPTY;
        active = false;
    }

    static void pumpCompute() {
        var worker = VaporClient.worker();
        if (worker == null || !worker.usesCompute() || !RenderSystem.isOnRenderThread()) return;
        for (int i = 0; i < 2; i++) {
            var input = worker.takeCompute();
            if (input == null) return;
            try {
                if (compute == null) {
                    if (input.pulses().isEmpty() && input.emitters().isEmpty()
                            && input.gusts().isEmpty() && input.windEmitters().isEmpty()) continue;
                    compute = new VaporCompute();
                }
                compute.step(input, texture());
            } catch (RuntimeException failure) {
                LogUtils.getLogger().warn("OpenGL voxel field update failed. Switching to the CPU solver.", failure);
                worker.fallBack(input);
                releaseWorld();
                return;
            }
        }
    }

    public static void prepareFrame() {
        pumpCompute();
        var worker = VaporClient.worker();
        if (worker != null && worker.usesCompute()) {
            active = computeActive();
            if (active) updateUniforms(compute.origin(), compute.bounds(), compute.publishedAt());
            return;
        }
        FieldFrames<VaporVolume.Snapshot> frames = VaporClient.frames();
        var current = frames.current();
        var previous = frames.previous();
        active = texture != 0 && (current != null || previous != null);
        if (!active) return;

        var reference = current == null ? previous : current;
        if (uploaded != frames) {
            Bounds previousBounds = previous == null ? Bounds.EMPTY : previous.boundsAt(reference.origin);
            var next = current == null ? previousBounds : current.bounds.union(previousBounds);
            var region = bounds.union(next);
            upload(region, previous, current, reference);
            uploaded = frames;
            bounds = next;
        }

        updateUniforms(reference.origin, bounds, frames.publishedAt());
    }

    private static void updateUniforms(Position reference, Bounds region, long publishedAt) {
        var camera = CameraUniforms.getUnshiftedCameraPosition();
        var client = Minecraft.getInstance();
        var entity = client.getCameraEntity();
        if (entity == null) center.zero();
        else {
            var eye = entity.getEyePosition(client.getFrameTime());
            center.set(eye.x - camera.x, eye.y - camera.y, eye.z - camera.z);
        }
        origin.set(reference.x() - camera.x, reference.y() - camera.y, reference.z() - camera.z);
        minimum.set(origin).add(region.minX(), region.minY(), region.minZ());
        maximum.set(origin).add(region.maxX(), region.maxY(), region.maxZ());
        blend = Math.max(0.0, Math.min(1.0, (System.nanoTime() - publishedAt) / TICK_NANOS));
    }

    private static void upload(Bounds region, VaporVolume.Snapshot previous,
                               VaporVolume.Snapshot current, VaporVolume.Snapshot occupancy) {
        // The texture uses the newest origin. Align the old frame by world block to keep blending on movement.
        int dx = previous == null ? 0 : occupancy.origin.x() - previous.origin.x();
        int dy = previous == null ? 0 : occupancy.origin.y() - previous.origin.y();
        int dz = previous == null ? 0 : occupancy.origin.z() - previous.origin.z();
        boolean sameOrigin = dx == 0 && dy == 0 && dz == 0;
        staging.clear();
        for (int z = region.minZ(); z < region.maxZ(); z++) {
            for (int y = region.minY(); y < region.maxY(); y++) {
                for (int x = region.minX(); x < region.maxX(); x++) {
                    int i = VaporVolume.index(x, y, z);
                    int earlier = previous == null ? 0 : sameOrigin ? previous.density(i)
                            : previous.density(x + dx, y + dy, z + dz);
                    staging.put((byte) earlier);
                    staging.put((byte) (current == null ? 0 : current.density(i)));
                    staging.put((byte) (occupancy.open(i) ? 255 : 0));
                    staging.put((byte) 0);
                }
            }
        }
        staging.flip();
        VoxelTextureUpload.withUnpackState(texture, () -> glTexSubImage3D(GL_TEXTURE_3D, 0, region.minX(), region.minY(), region.minZ(),
                region.maxX() - region.minX(), region.maxY() - region.minY(), region.maxZ() - region.minZ(),
                GL_RGBA, GL_UNSIGNED_BYTE, staging));
    }
}
