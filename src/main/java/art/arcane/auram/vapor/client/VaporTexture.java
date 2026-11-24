package art.arcane.auram.vapor.client;

import art.arcane.auram.field.FieldFrames;
import art.arcane.auram.field.FieldTicker;
import art.arcane.auram.field.VoxelGrid.Bounds;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CameraUniforms;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING;

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
    private static final Vector3f minimum = new Vector3f();
    private static final Vector3f maximum = new Vector3f();
    private static double blend;
    private static boolean active;

    private VaporTexture() {
    }

    public static void uniforms(UniformHolder uniforms) {
        uniforms.uniform1b(PER_FRAME, "auramVaporActive", () -> active);
        uniforms.uniform1f(PER_FRAME, "auramVaporBlend", () -> blend);
        uniforms.uniform3f(PER_FRAME, "auramVaporOrigin", () -> origin);
        uniforms.uniform3f(PER_FRAME, "auramVaporMinimum", () -> minimum);
        uniforms.uniform3f(PER_FRAME, "auramVaporMaximum", () -> maximum);
    }

    public static int texture() {
        if (texture == 0) {
            texture = glGenTextures();
            staging = BufferUtils.createByteBuffer(VaporVolume.CELLS * 4);
            withUnpackState(() -> {
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

    public static void prepareFrame() {
        FieldFrames<VaporVolume.Snapshot> frames = VaporClient.frames();
        var current = frames.current();
        var previous = frames.previous();
        active = texture != 0 && (current != null || previous != null);
        if (!active) return;

        var reference = current == null ? previous : current;
        if (uploaded != frames) {
            var next = current == null ? previous.bounds : previous == null ? current.bounds : current.bounds.union(previous.bounds);
            var region = bounds.union(next);
            upload(region, previous, current, reference);
            uploaded = frames;
            bounds = next;
        }

        var camera = CameraUniforms.getUnshiftedCameraPosition();
        origin.set(reference.origin.x() - camera.x, reference.origin.y() - camera.y, reference.origin.z() - camera.z);
        minimum.set(origin).add(bounds.minX(), bounds.minY(), bounds.minZ());
        maximum.set(origin).add(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        blend = Math.max(0.0, Math.min(1.0, (System.nanoTime() - frames.publishedAt()) / TICK_NANOS));
    }

    private static void upload(Bounds region, VaporVolume.Snapshot previous,
                               VaporVolume.Snapshot current, VaporVolume.Snapshot occupancy) {
        staging.clear();
        for (int z = region.minZ(); z < region.maxZ(); z++) {
            for (int y = region.minY(); y < region.maxY(); y++) {
                for (int x = region.minX(); x < region.maxX(); x++) {
                    int i = VaporVolume.index(x, y, z);
                    staging.put((byte) (previous == null ? 0 : previous.density(i)));
                    staging.put((byte) (current == null ? 0 : current.density(i)));
                    staging.put((byte) (occupancy.open(i) ? 255 : 0));
                    staging.put((byte) 0);
                }
            }
        }
        staging.flip();
        withUnpackState(() -> glTexSubImage3D(GL_TEXTURE_3D, 0, region.minX(), region.minY(), region.minZ(),
                region.maxX() - region.minX(), region.maxY() - region.minY(), region.maxZ() - region.minZ(),
                GL_RGBA, GL_UNSIGNED_BYTE, staging));
    }

    private static void withUnpackState(Runnable upload) {
        int bound = glGetInteger(GL_TEXTURE_BINDING_3D);
        int buffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int[] parameters = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_IMAGE_HEIGHT,
                GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS, GL_UNPACK_SKIP_IMAGES};
        int[] previous = new int[parameters.length];
        try {
            glBindTexture(GL_TEXTURE_3D, texture);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            for (int i = 0; i < parameters.length; i++) {
                previous[i] = glGetInteger(parameters[i]);
                glPixelStorei(parameters[i], i == 0 ? 4 : 0);
            }
            upload.run();
        } finally {
            for (int i = 0; i < parameters.length; i++) glPixelStorei(parameters[i], previous[i]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
            glBindTexture(GL_TEXTURE_3D, bound);
        }
    }
}
