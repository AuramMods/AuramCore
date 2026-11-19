package art.arcane.auram.smoke;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class TomeHudSmoke {
    private static final String STORAGE = "akashictome:data";
    private static final int FRAMES = 300;
    private static volatile boolean profilingCopies;
    private static int copies;
    private static int phase;

    public static void observeCopy(CompoundTag tag) {
        if (profilingCopies && tag.getCompound(STORAGE).size() > 1
                && Thread.currentThread().getName().equals("Render thread")) copies++;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!"tomehud".equals(System.getProperty("auram.smoke")) || event.phase != TickEvent.Phase.END || phase == 2) return;
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (phase == 0 && client.screen instanceof TitleScreen) {
                phase = 1;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                client.createWorldOpenFlows().createFreshLevel("auram-tome-qa",
                        new LevelSettings("Auram Tome QA", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(104729L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            } else if (phase == 1 && client.level != null && client.player != null && client.screen == null) {
                phase = 2;
                run(client);
                Files.writeString(Path.of("auram-tomehud-pass.txt"), "PASS Akashic Tome HUD and morph isolation\n");
                client.stop();
            }
        } catch (Throwable t) {
            phase = 2;
            com.mojang.logging.LogUtils.getLogger().error("Tome HUD fixture failed", t);
            try { Files.writeString(Path.of("auram-tomehud-failed.txt"), t.toString()); } catch (Exception ignored) { }
            client.stop();
        }
    }

    private static void run(Minecraft client) throws Exception {
        boolean reference = Boolean.getBoolean("auram.smoke.tomeReference");
        String script = Files.readString(Path.of(System.getProperty("auram.smoke.tomeScript")));
        int start = script.indexOf(".withTag(") + ".withTag(".length();
        int end = script.indexOf("});", start) + 1;
        CompoundTag source = TagParser.parseTag(script.substring(start, end));
        ItemStack tome = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("akashictome:tome")));
        tome.setTag(source);
        int guides = source.getCompound(STORAGE).size();
        check(guides > 20, "fixture uses the pack's full tome");
        CompoundTag original = source.copy();
        var table = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("projecte:transmutation_table"));
        BlockPos target = client.player.blockPosition().above();
        check(table != null && table != Blocks.AIR, "ProjectE block for alias fixture");

        Object hud = Class.forName("vazkii.akashictome.client.HUDHandler").getConstructor().newInstance();
        Method draw = hud.getClass().getMethod("onDrawScreen", RenderGuiOverlayEvent.Post.class);
        var morph = Class.forName("vazkii.akashictome.MorphingHandler")
                .getMethod("getShiftStackForMod", ItemStack.class, String.class);
        var inventory = client.player.getInventory();
        inventory.setItem(inventory.selected, tome);
        client.level.setBlock(target, table.defaultBlockState(), 0);
        client.hitResult = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);

        RecordingGraphics mainHand = new RecordingGraphics(client, true);
        frame(client, hud, draw, mainHand);
        check(mainHand.items == (reference ? 4 : 1), "item previews per frame: " + mainHand.items);
        String previewItem = mainHand.firstItem;
        List<String> previewText = List.copyOf(mainHand.firstText);
        check(previewItem.contains("patchouli:guide_book") && previewItem.contains("auram:auram"),
                "aliased block selects the Auram guide");
        check(previewText.size() == 2, "draws the guide title and morph hint");

        inventory.setItem(inventory.selected, ItemStack.EMPTY);
        inventory.setItem(40, tome);
        RecordingGraphics offhand = new RecordingGraphics(client, true);
        frame(client, hud, draw, offhand);
        check(previewItem.equals(offhand.firstItem) && previewText.equals(offhand.firstText), "offhand preview matches");

        client.level.setBlock(target, Blocks.STONE.defaultBlockState(), 0);
        RecordingGraphics vanilla = new RecordingGraphics(client, true);
        frame(client, hud, draw, vanilla);
        check(vanilla.items == 0, "switching to an unmatched vanilla block hides preview");
        client.level.setBlock(target, table.defaultBlockState(), 0);
        CompoundTag unregistered = original.copy();
        unregistered.getCompound(STORAGE).remove("auram");
        ItemStack noGuide = new ItemStack(tome.getItem());
        noGuide.setTag(unregistered);
        inventory.setItem(40, noGuide);
        RecordingGraphics missing = new RecordingGraphics(client, true);
        frame(client, hud, draw, missing);
        check(missing.items == 0, "unmatched alias has no preview");
        ItemStack defined = tome.copy();
        defined.getOrCreateTag().putString("akashictome:definedMod", "auram");
        inventory.setItem(40, defined);
        RecordingGraphics sameGuide = new RecordingGraphics(client, true);
        frame(client, hud, draw, sameGuide);
        check(sameGuide.items == 0, "the current defined guide keeps its no-morph behavior");

        inventory.setItem(inventory.selected, tome);
        inventory.setItem(40, ItemStack.EMPTY);
        for (int i = 0; i < FRAMES; i++) frame(client, hud, draw, new RecordingGraphics(client, false));
        List<Double> timings = new ArrayList<>();
        copies = 0;
        profilingCopies = true;
        try {
            for (int run = 0; run < 7; run++) {
                long startNs = System.nanoTime();
                for (int i = 0; i < FRAMES; i++) frame(client, hud, draw, new RecordingGraphics(client, false));
                timings.add((System.nanoTime() - startNs) / 1_000_000.0);
            }
        } finally { profilingCopies = false; }
        check(reference ? copies >= FRAMES * 7 * 4 : copies == 0, "full-tome copy count: " + copies);
        check(original.equals(tome.getTag()), "HUD previews never modify the live tome");
        ItemStack morphed = (ItemStack) morph.invoke(null, tome.copy(), "auram");
        check(morphed.getTag().getCompound(STORAGE).size() == guides - 1,
                "the actual morph retains the rest of the library");
        var result = Map.of("item", previewItem, "text", previewText, "guides", guides,
                "before_after_tome_match", true, "batch_frames", FRAMES, "milliseconds", timings,
                "full_tome_copies", copies, "previews_per_frame", mainHand.items, "reference", reference);
        Files.writeString(Path.of("tomehud-results.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    }

    private static void frame(Minecraft client, Object hud, Method draw, RecordingGraphics g) throws Exception {
        for (VanillaGuiOverlay overlay : List.of(VanillaGuiOverlay.VIGNETTE, VanillaGuiOverlay.CROSSHAIR,
                VanillaGuiOverlay.HOTBAR, VanillaGuiOverlay.CHAT_PANEL)) {
            draw.invoke(hud, new RenderGuiOverlayEvent.Post(client.getWindow(), g, 0, overlay.type()));
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class RecordingGraphics extends GuiGraphics {
        private final boolean details;
        private int items;
        private String firstItem;
        private final List<String> firstText = new ArrayList<>();

        RecordingGraphics(Minecraft client, boolean details) {
            super(client, client.renderBuffers().bufferSource());
            this.details = details;
        }

        @Override public void renderItem(ItemStack stack, int x, int y) {
            items++;
            if (details && items == 1) {
                CompoundTag serialized = stack.save(new CompoundTag()).copy();
                serialized.getCompound("tag").remove(STORAGE);
                firstItem = x + "," + y + " " + serialized;
            }
        }

        @Override public int drawString(Font font, Component text, int x, int y, int color) {
            if (details && items == 1) firstText.add(x + "," + y + "," + color + " " + text.getString());
            return 0;
        }

        @Override public int drawString(Font font, String text, int x, int y, int color) {
            if (details && items == 1) firstText.add(x + "," + y + "," + color + " " + text);
            return 0;
        }
    }
}
