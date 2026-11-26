package art.arcane.auram.smoke;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke")
public final class RecipeSnapshotSmoke {
    private RecipeSnapshotSmoke() {}

    @SubscribeEvent
    public static void capture(ServerStartedEvent event) throws Exception {
        if (Boolean.getBoolean("auram.smoke.recipeSnapshot")) capture(event.getServer(), "");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterReload(OnDatapackSyncEvent event) throws Exception {
        if (event.getPlayer() == null && Boolean.getBoolean("auram.smoke.recipeSnapshot")) {
            capture(event.getPlayerList().getServer(), "-reload");
        }
    }

    private static void capture(MinecraftServer server, String phase) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var recipes = new ArrayList<String>();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
                if (recipe.getClass().getName().equals("blusunrize.immersiveengineering.common.crafting.GeneratedListRecipe")) {
                    // Full serialization needs Immersive Engineering's generated list prepared on player sync.
                    recipes.add(recipe.getId() + "\tgenerated-list");
                    continue;
                }
                buffer.clear();
                new ClientboundUpdateRecipesPacket(List.of(recipe)).write(buffer);
                digest.update(buffer.nioBuffer());
                recipes.add(recipe.getId() + "\t" + buffer.writerIndex() + "\t" + HexFormat.of().formatHex(digest.digest()));
            }
        } finally {
            buffer.release();
        }
        recipes.sort(String::compareTo);
        Files.write(Path.of("recipe-snapshot" + phase + ".tsv"), recipes);

        var tags = new ArrayList<String>();
        server.registryAccess().registries().forEach(entry -> entry.value().getTags().forEach(tag -> {
            var members = tag.getSecond().stream().map(holder -> holder.unwrapKey().orElseThrow().location().toString()).toList();
            tags.add(entry.key().location() + "\t" + tag.getFirst().location() + "\t" + String.join(",", members));
        }));
        tags.sort(String::compareTo);
        Files.write(Path.of("tag-snapshot" + phase + ".tsv"), tags);
        System.out.println("AURAM_RECIPE_SNAPSHOT phase=" + (phase.isEmpty() ? "boot" : "reload")
                + " recipes=" + recipes.size() + " tags=" + tags.size());
    }
}
