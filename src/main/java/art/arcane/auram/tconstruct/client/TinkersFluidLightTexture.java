package art.arcane.auram.tconstruct.client;

import art.arcane.auram.field.VoxelGrid;
import art.arcane.auram.field.VoxelGrid.Bounds;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.field.client.VoxelTextureUpload;
import art.arcane.auram.light.LightClient;
import art.arcane.auram.vapor.client.VanillaEntityEffects;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.fml.ModList;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;

/** Sparse sources uploaded before the shader's existing light-volume propagation. */
public final class TinkersFluidLightTexture {
    public static final String SAMPLER = "auramTinkersFluidLight";
    private static final int SIDE = BLOCKS.side();
    private static final int SCAN_INTERVAL = 2;

    private static final Vector3f origin = new Vector3f();
    private static final Vector3f minimum = new Vector3f();
    private static final Vector3f maximum = new Vector3f();
    private static final Int2IntOpenHashMap ambientLights = new Int2IntOpenHashMap();
    private static Int2IntOpenHashMap uploaded = new Int2IntOpenHashMap();
    private static Int2IntOpenHashMap scan = new Int2IntOpenHashMap();
    private static ClientLevel level;
    private static Position base;
    private static long scannedAt = Long.MIN_VALUE;
    private static int texture;
    private static ByteBuffer staging;
    private static boolean active;
    private static boolean hadApiLights;

    private TinkersFluidLightTexture() {}

    public static void uniforms(UniformHolder uniforms) {
        uniforms.uniform1b(PER_FRAME, "auramTinkersFluidLightActive", () -> active);
        uniforms.uniform3f(PER_FRAME, "auramTinkersFluidLightOrigin", () -> origin);
        uniforms.uniform3f(PER_FRAME, "auramTinkersFluidLightMinimum", () -> minimum);
        uniforms.uniform3f(PER_FRAME, "auramTinkersFluidLightMaximum", () -> maximum);
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
                glTexImage3D(GL_TEXTURE_3D, 0, GL_RGBA8, SIDE, SIDE, SIDE, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            });
        }
        return texture;
    }

    public static boolean blissActive() {
        if (!IrisApi.getInstance().isShaderPackInUse()) return false;
        String pack = Iris.getCurrentPackName();
        return "AuramBliss".equalsIgnoreCase(pack) || "AuramBliss.zip".equalsIgnoreCase(pack);
    }

    public static void prepareFrame() {
        ClientLevel current = Minecraft.getInstance().level;
        var pack = Iris.getCurrentPack().orElse(null);
        if (texture == 0 || current == null || !blissActive() || pack == null
                || !pack.getShaderPackOptions().getOptionValues().getBooleanValueOrDefault("LPV_ENABLED")) {
            active = false;
            level = null;
            return;
        }

        var camera = CameraUniforms.getUnshiftedCameraPosition();
        Position nextBase = VoxelGrid.originAround((int) Math.floor(camera.x), (int) Math.floor(camera.y),
                (int) Math.floor(camera.z));
        long now = current.getGameTime();
        boolean scanAmbient = level != current || !nextBase.equals(base) || now < scannedAt
                || now - scannedAt >= SCAN_INTERVAL;
        if (scanAmbient) {
            level = current;
            base = nextBase;
            scannedAt = now;
            ambientLights.clear();
            if (ModList.get().isLoaded("tconstruct")) TinkersFluidLights.collect(current, base, ambientLights);
            VanillaEntityEffects.collectLights(current, base, ambientLights);
        }

        boolean apiLights = LightClient.hasSources(current);
        if (scanAmbient || apiLights || hadApiLights) {
            scan.clear();
            scan.putAll(ambientLights);
            if (apiLights) LightClient.collect(current, base, scan);
            if (!scan.equals(uploaded)) {
                upload(scan);
                var previous = uploaded;
                uploaded = scan;
                scan = previous;
            }
        }
        hadApiLights = apiLights;
        active = !uploaded.isEmpty();
        if (active) {
            origin.set(base.x() - camera.x, base.y() - camera.y, base.z() - camera.z);
        }
    }

    private static void upload(Int2IntOpenHashMap colors) {
        if (colors.isEmpty()) return;
        Bounds region = Bounds.EMPTY;
        for (int index : colors.keySet()) {
            region = region.include(index % SIDE, index / SIDE % SIDE, index / (SIDE * SIDE));
        }
        minimum.set(region.minX(), region.minY(), region.minZ());
        maximum.set(region.maxX(), region.maxY(), region.maxZ());

        int width = region.maxX() - region.minX();
        int height = region.maxY() - region.minY();
        int depth = region.maxZ() - region.minZ();
        if (staging == null) staging = BufferUtils.createByteBuffer(BLOCKS.cells() * 4);
        staging.clear();
        staging.limit(width * height * depth * 4);
        MemoryUtil.memSet(MemoryUtil.memAddress(staging), 0, staging.remaining());

        var entries = colors.int2IntEntrySet().fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            int index = entry.getIntKey();
            int x = index % SIDE - region.minX();
            int y = index / SIDE % SIDE - region.minY();
            int z = index / (SIDE * SIDE) - region.minZ();
            int offset = (x + width * (y + height * z)) * 4;
            int color = entry.getIntValue();
            staging.put(offset, (byte) (color >> 16));
            staging.put(offset + 1, (byte) (color >> 8));
            staging.put(offset + 2, (byte) color);
            staging.put(offset + 3, (byte) (color >>> 24));
        }
        Bounds bounds = region;
        VoxelTextureUpload.withUnpackState(texture, () -> glTexSubImage3D(GL_TEXTURE_3D, 0,
                bounds.minX(), bounds.minY(), bounds.minZ(), width, height, depth,
                GL_RGBA, GL_UNSIGNED_BYTE, staging));
    }
}
