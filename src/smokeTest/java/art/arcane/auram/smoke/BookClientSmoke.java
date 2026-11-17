package art.arcane.auram.smoke;

import art.arcane.auram.Auram;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.ClientBookRegistry;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.gui.GuiBookLanding;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Queue;

/** Actual Patchouli loading, recipe, tab and rendering checks in a disposable client. */
@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class BookClientSmoke {
    private static int phase, frames;
    private static Book book;
    private static final Queue<Spread> spreads = new ArrayDeque<>();
    private static String capture;
    private static int rendered;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!System.getProperty("auram.smoke", "").equals("book") || event.phase != TickEvent.Phase.END
                || phase == 3) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (phase == 0 && client.screen != null) {
                phase = 1;
                LevelSettings settings = new LevelSettings("Auram Guide QA", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("auram-guide-" + System.currentTimeMillis(), settings,
                        new WorldOptions(104729L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if (phase == 1 && client.level != null && client.player != null) {
                book = BookRegistry.INSTANCE.books.get(ResourceLocation.parse("auram:auram"));
                require(book != null, "Auram guide is registered");
                ClientBookRegistry.INSTANCE.reload();
                require(!book.getContents().isErrored(), "Book content error: " + book.getContents().getException());
                require(book.getContents().categories.size() == 5, "Five categories loaded");
                require(book.getContents().entries.size() == 20, "Twenty entries loaded");
                ItemStack guide = book.getBookItem();
                require(guide.hasTag() && guide.getTag().getString("patchouli:book").equals("auram:auram"), "Correct guide NBT");
                CreativeModeTabs.tryRebuildTabContents(client.level.enabledFeatures(), true, client.level.registryAccess());
                require(Auram.AURAM_TAB.get().getDisplayItems().stream().anyMatch(stack -> ItemStack.isSameItemSameTags(stack, guide)),
                        "Guide is present in Auram creative tab");
                var recipe = client.level.getRecipeManager().byKey(ResourceLocation.parse("auram:guide_book")).orElseThrow();
                require(ItemStack.isSameItemSameTags(recipe.getResultItem(client.level.registryAccess()), guide), "Recipe crafts the Auram guide");
                require(recipe.getIngredients().size() == 3, "Guide has three ingredients");
                for (var item : new net.minecraft.world.item.Item[]{Items.BOOK, Items.COBBLESTONE, Items.PURPLE_DYE}) {
                    require(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(item))), "Guide ingredient: " + item);
                }
                Files.createDirectories(Path.of("guide-screenshots"));
                book.getContents().entries.values().stream().sorted(Comparator.comparing(entry -> entry.getId().toString()))
                        .forEach(entry -> {
                            require(client.font.width(entry.getName().withStyle(book.getFontStyle())) <= 116,
                                    "Entry heading is too wide: " + entry.getName().getString());
                            for (var page : entry.getPages()) {
                                if (page.sourceObject.has("title")) {
                                    String title = page.sourceObject.get("title").getAsString();
                                    require(client.font.width(Component.literal(title).withStyle(book.getFontStyle())) <= 116,
                                            "Page heading is too wide: " + title);
                                }
                            }
                            for (int page = 0; page < entry.getPages().size(); page += 2) spreads.add(new Spread(entry, page / 2));
                        });
                phase = 2;
                client.setScreen(new GuiBookLanding(book));
                capture = "00-landing";
                frames = 0;
            } else if (phase == 2 && capture == null) {
                Spread spread = spreads.poll();
                if (spread == null) {
                    Files.writeString(Path.of("auram-book-pass.txt"), "PASS five categories, twenty entries, guide recipe, creative tab and " + rendered + " rendered spreads\n");
                    System.out.println("QA_EVT {\"event\":\"auram-guide\",\"status\":\"pass\",\"renderedSpreads\":" + rendered + "}");
                    phase = 3;
                    client.stop();
                } else {
                    client.setScreen(new GuiBookEntry(book, spread.entry, spread.page));
                    capture = spread.entry.getId().getPath().replace('/', '-') + "-" + spread.page;
                    frames = 0;
                }
            }
        } catch (Throwable error) { fail(client, error); }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!System.getProperty("auram.smoke", "").equals("book") || event.phase != TickEvent.Phase.END
                || phase != 2 || capture == null || ++frames < 3) return;
        Minecraft client = Minecraft.getInstance();
        try (NativeImage screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            screenshot.writeToFile(Path.of("guide-screenshots", capture + ".png"));
            rendered++;
            capture = null;
        } catch (Throwable error) { fail(client, error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void fail(Minecraft client, Throwable error) {
        phase = 3;
        error.printStackTrace();
        try { Files.writeString(Path.of("auram-book-failed.txt"), error.toString()); }
        catch (Exception writeError) { writeError.printStackTrace(); }
        client.stop();
    }

    private record Spread(BookEntry entry, int page) { }
}
