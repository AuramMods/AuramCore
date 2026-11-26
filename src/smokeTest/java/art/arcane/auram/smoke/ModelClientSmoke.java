package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Measure reload-scoped model lifetimes and inspect models through the installed renderers. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class ModelClientSmoke {
    private static final String MODE = "memory-models";
    private static final boolean TITLE_ONLY = Boolean.getBoolean("auram.smoke.modelTitleOnly");
    private static final boolean TARGETED = Boolean.getBoolean("auram.smoke.targetedModels");
    private static final String OVERRIDE = "file/Auram QA Models";
    private static final List<Map<String, Object>> checks = new ArrayList<>();
    private static final List<Long> frameNanos = new ArrayList<>();
    private static CompletableFuture<?> pending;
    private static BlockPos platform;
    private static List<String> initialPacks;
    private static Integer firstBakery;
    private static String defaultSword;
    private static int phase, frames;
    private static long previousFrame, reloadStarted;
    private static long firstRenderAt;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!MODE.equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END || phase == 9) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (phase == 0 && client.screen instanceof TitleScreen) {
                verifyAudioAndWindow(client);
                if (++frames < 30) return;
                firstRenderAt = ManagementFactory.getRuntimeMXBean().getUptime();
                if (Boolean.getBoolean("auram.smoke.heapDump")) {
                    Map<String, Object> sample = inspectRegistry(client);
                    var diagnostic = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
                    diagnostic.dumpHeap(Path.of("model-title.hprof").toAbsolutePath().toString(), true);
                    checks.add(sample);
                    finish(client);
                    return;
                }
                Files.createDirectories(Path.of("resourcepacks/Auram QA Models/assets/minecraft/models/item"));
                Files.writeString(Path.of("resourcepacks/Auram QA Models/pack.mcmeta"),
                        "{\"pack\":{\"pack_format\":15,\"description\":\"Auram model smoke fixture\"}}");
                Files.writeString(Path.of("resourcepacks/Auram QA Models/assets/minecraft/models/item/diamond_sword.json"),
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/redstone\"}}");
                initialPacks = List.copyOf(client.getResourcePackRepository().getSelectedIds());
                if (TITLE_ONLY) {
                    pending = CompletableFuture.completedFuture(null);
                    phase = 3;
                    frames = 0;
                    return;
                }
                phase = 1;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                client.createWorldOpenFlows().createFreshLevel("auram-model-qa",
                        new LevelSettings("Auram Models QA", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(104729L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            } else if (phase == 1 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
                phase = 2;
                var server = client.getSingleplayerServer();
                pending = server.submit(() -> {
                    var player = server.getPlayerList().getPlayers().get(0);
                    var level = player.serverLevel();
                    level.setDayTime(6000);
                    BlockPos base = player.blockPosition().atY(level.getMaxBuildHeight() - 32);
                    for (var pos : BlockPos.betweenClosed(base.offset(-7, 0, -7), base.offset(7, 0, 7))) {
                        level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                    Block pipe = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekanism", "basic_mechanical_pipe"));
                    check(pipe != null && pipe != Blocks.AIR, "Mekanism pipe is registered");
                    level.setBlockAndUpdate(base.offset(0, 1, 3), pipe.defaultBlockState());
                    level.setBlockAndUpdate(base.offset(1, 1, 3), pipe.defaultBlockState());
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
                    player.teleportTo(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5, 0, 5);
                    return base;
                });
            } else if (phase == 2 && pending.isDone()) {
                platform = (BlockPos) pending.join();
                if (client.player == null || client.level == null
                        || Math.abs(client.player.getY() - platform.getY() - 1) > 1.5
                        || !client.level.getBlockState(platform).is(Blocks.SMOOTH_STONE)) return;
                if (client.screen != null) client.setScreen(null);
                phase = 3;
                frames = 0;
            } else if (phase >= 3 && pending.isDone() && frames >= 90) {
                pending.join();
                inspectStage(client);
                if (phase >= (TITLE_ONLY ? 5 : 7)) {
                    finish(client);
                    return;
                }
                int enableAt = TITLE_ONLY ? 3 : 5;
                if (phase == enableAt || phase == enableAt + 1) {
                    var repo = client.getResourcePackRepository();
                    repo.reload();
                    List<String> desired = new ArrayList<>(initialPacks);
                    if (phase == enableAt) desired.add(OVERRIDE);
                    repo.setSelected(desired);
                    check(repo.getSelectedIds().contains(OVERRIDE) == (phase == enableAt), "Resource pack selection changed");
                }
                phase++;
                frames = 0;
                previousFrame = 0;
                reloadStarted = System.nanoTime();
                pending = client.reloadResourcePacks().thenRun(() -> {
                    try {
                        Files.writeString(Path.of("model-reload-" + phase + ".txt"),
                                Long.toString(System.nanoTime() - reloadStarted));
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                });
            }
        } catch (Throwable error) {
            phase = 9;
            error.printStackTrace();
            client.stop();
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!MODE.equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END
                || phase < 3 || phase > 7 || !pending.isDone()) return;
        long now = System.nanoTime();
        if (previousFrame != 0) frameNanos.add(now - previousFrame);
        previousFrame = now;
        frames++;
    }

    private static void inspectStage(Minecraft client) throws Exception {
        Map<String, Object> result = TARGETED ? inspectSelectedModels(client) : inspectRegistry(client);
        ModelBakery bakery = client.getModelManager().getModelBakery();
        // Avoid retaining old bakeries while testing repeated reloads.
        int bakeryId = System.identityHashCode(bakery);
        check(!Integer.valueOf(bakeryId).equals(firstBakery), "Reload installed a new bakery");
        firstBakery = bakeryId;
        var sword = client.getItemRenderer().getModel(new ItemStack(Items.DIAMOND_SWORD), client.level, client.player, 0);
        String swordDigest = modelFingerprint(sword, null);
        if (phase == 3) defaultSword = swordDigest;
        check(swordDigest.equals(defaultSword) == (phase != (TITLE_ONLY ? 4 : 6)), "Resource-pack change applied and reverted the item model");
        result.put("sword", swordDigest);
        if (client.level != null) {
            var transmitter = Class.forName("mekanism.client.render.transmitter.RenderTransmitterBase");
            var bake = transmitter.getDeclaredMethod("getBakedQuads", List.class,
                    net.minecraft.client.renderer.texture.TextureAtlasSprite.class, Level.class);
            bake.setAccessible(true);
            var sprite = client.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                    .getSprite(new ResourceLocation("minecraft", "block/water_still"));
            var quads = (List<?>) bake.invoke(null, List.of("northNORMAL", "southNORMAL"), sprite, client.level);
            check(!quads.isEmpty(), "Mekanism transmitter produced late-baked content quads");
            var pipe = client.level.getBlockState(platform.offset(0, 1, 3));
            check(ForgeRegistries.BLOCKS.getKey(pipe.getBlock()).equals(new ResourceLocation("mekanism", "basic_mechanical_pipe")),
                    "Mekanism pipe reached client");
            check(client.getBlockRenderer().getBlockModel(pipe) != client.getModelManager().getMissingModel(),
                    "Mekanism pipe has a rendered model");
            result.put("transmitter", quadFingerprint(quads));
            result.put("transmitter_quads", quads.size());
        }
        result.put("phase", phase);
        result.put("uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        result.put("frames_ns", List.copyOf(frameNanos));
        frameNanos.clear();
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(Path.of("model-stage-" + phase + ".png"));
        }
        checks.add(result);
        if (TARGETED && Boolean.getBoolean("auram.smoke.modelHeapDumps") && (phase == 3 || phase == 7)) {
            var diagnostic = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            diagnostic.dumpHeap(Path.of("model-stage-" + phase + ".hprof").toAbsolutePath().toString(), true);
        }
    }

    private static Map<String, Object> inspectSelectedModels(Minecraft client) throws Exception {
        var manager = client.getModelManager();
        var variants = new TreeMap<String, String>();
        // A registry-wide traversal would materialize the lazy models being measured.
        for (String id : List.of("auram:rock", "auram:rock_deepslate", "draconicevolution:draconic_bow")) {
            var model = manager.getModel(new ModelResourceLocation(ResourceLocation.parse(id), "inventory"));
            check(model != manager.getMissingModel(), "Model is present for " + id);
            variants.put(id, modelFingerprint(model, null));
        }
        var result = new TreeMap<String, Object>();
        result.put("variants", variants);
        var iris = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        result.put("shader_active", iris.getMethod("isShaderPackInUse").invoke(iris.getMethod("getInstance").invoke(null)));
        result.put("dynamic_resources", Arrays.stream(manager.getModelBakery().getClass().getInterfaces())
                .anyMatch(type -> type.getName().equals("org.embeddedt.modernfix.duck.IExtendedModelBakery")));
        return result;
    }

    private static Map<String, Object> inspectRegistry(Minecraft client) throws Exception {
        var manager = client.getModelManager();
        var bakery = manager.getModelBakery();
        Map<ResourceLocation, BakedModel> baked = bakery.getBakedTopLevelModels();
        var missing = manager.getMissingModel();
        check(missing != null && manager.getModel(new ModelResourceLocation("auram", "nonexistent_fixture_model", "inventory")) == missing,
                "Missing model fallback works");
        var top = ModelBakery.class.getDeclaredField("f_119214_");
        top.setAccessible(true);
        var staging = (Map<?, ?>) top.get(bakery);
        var ids = new TreeMap<String, Boolean>();
        var variants = new TreeMap<String, String>();
        int missingCount = 0;
        for (var entry : baked.entrySet()) {
            boolean absent = entry.getValue() == missing;
            if (absent) missingCount++;
            String id = entry.getKey().toString();
            ids.put(id, absent);
            if (id.startsWith("auram:") && id.contains("rock") || id.startsWith("forbidden_arcanus:magnetized_")
                    || id.equals("draconicevolution:draconic_bow#inventory")) {
                check(!absent, "Model is present for " + id);
                variants.put(id, modelFingerprint(entry.getValue(), null));
            }
        }
        check(variants.keySet().stream().anyMatch(id -> id.startsWith("auram:") && id.contains("rock")), "Auram rocks present");
        check(variants.keySet().stream().anyMatch(id -> id.startsWith("forbidden_arcanus:magnetized_")), "Magnetized boot variants present");
        check(variants.containsKey("draconicevolution:draconic_bow#inventory"), "Draconic custom model present");
        var result = new TreeMap<String, Object>();
        result.put("baked_models", baked.size());
        result.put("missing_models", missingCount);
        result.put("registry_fingerprint", fingerprint(ids.toString()));
        result.put("variants", variants);
        result.put("staging_entries", staging.size());
        result.put("staging_type", staging.getClass().getName());
        result.put("dynamic_resources", Arrays.stream(bakery.getClass().getInterfaces())
                .anyMatch(type -> type.getName().equals("org.embeddedt.modernfix.duck.IExtendedModelBakery")));
        return result;
    }

    private static String modelFingerprint(BakedModel model, BlockState state) throws Exception {
        var quads = new ArrayList<BakedQuad>(model.getQuads(state, null, RandomSource.create(104729), ModelData.EMPTY, null));
        for (Direction face : Direction.values()) {
            quads.addAll(model.getQuads(state, face, RandomSource.create(104729), ModelData.EMPTY, null));
        }
        return model.getClass().getName() + "/" + model.isCustomRenderer() + "/" + quadFingerprint(quads);
    }

    private static String quadFingerprint(List<?> quads) throws Exception {
        var data = new StringBuilder();
        for (Object object : quads) {
            var quad = (BakedQuad) object;
            // Sprite packing can vary across reloads. Compare positions and sprite-relative UVs.
            var vertices = quad.getVertices();
            var sprite = quad.getSprite();
            data.append(sprite.contents().name()).append('/').append(quad.getDirection()).append('/').append(quad.getTintIndex());
            int stride = vertices.length / 4;
            for (int i = 0; i < vertices.length; i += stride) {
                for (int j = 0; j < 4; j++) data.append('/').append(vertices[i + j]);
                float u = (Float.intBitsToFloat(vertices[i + 4]) - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
                float v = (Float.intBitsToFloat(vertices[i + 5]) - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
                // Atlas sizes reach 16K in Auram. Normalize to remove float
                // cancellation when the same sprite moves between atlas slots.
                data.append('/').append(Math.round(u * 256)).append('/').append(Math.round(v * 256));
            }
        }
        return fingerprint(data.toString());
    }

    private static String fingerprint(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void verifyAudioAndWindow(Minecraft client) {
        check(GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Client window is hidden");
        check(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client is muted");
    }

    private static void finish(Minecraft client) throws Exception {
        Files.writeString(Path.of("models-client.json"), new GsonBuilder().setPrettyPrinting().create()
                .toJson(Map.of("stages", checks, "title_uptime_ms", firstRenderAt)) + "\n");
        Files.writeString(Path.of(MODE + "-pass.txt"), "PASS model inspection\n");
        phase = 9;
        client.stop();
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
