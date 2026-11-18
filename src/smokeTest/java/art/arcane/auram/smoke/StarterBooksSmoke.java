package art.arcane.auram.smoke;

import art.arcane.auram.smoke.mixin.PlayerListAccess;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Real advancement loot and Guns datapack, with a fresh player in a disposable world. */
public final class StarterBooksSmoke {
    public static void run(MinecraftServer server) {
        boolean original = Boolean.getBoolean("auram.smoke.expectStarterBooks");
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "StarterBookProbe"));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.moveTo(8, -62, 8, 0, 0);
        var players = (PlayerListAccess) server.getPlayerList();
        players.auramSmoke$players().add(player);
        players.auramSmoke$playersByUUID().put(player.getUUID(), player);
        level.addNewPlayer(player);
        try {
            verify(server, player, original);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void verify(MinecraftServer server, ServerPlayer player, boolean original) {
        var terramity = id("terramity:terramity_parent_advancement");
        var crystalcraft = id("crystalcraft_unlimited_java:recipe_unlock");
        var guideRecipe = server.getRecipeManager().byKey(id("ggunz:recipe")).orElseThrow();
        var functions = server.getFunctions();
        var loop = functions.get(id("ggunz:loop")).orElseThrow();
        var patchouli = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id("patchouli:guide_book")));
        var crystalopedia = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(id("crystalcraft_unlimited_java:crystalopedia")));
        Predicate<ItemStack> isTerramityGuide = stack -> stack.is(patchouli) && stack.hasTag()
                && "terramity:terramity_guidebook".equals(stack.getTag().getString("patchouli:book"));
        Predicate<ItemStack> isLogicraftGuide = stack -> stack.is(Items.WRITTEN_BOOK) && stack.hasTag()
                && "Logicraft Recipe Guide".equals(stack.getTag().getString("title"));

        ForgeEventFactory.firePlayerLoggedIn(player);
        require(player.getTags().contains("recipeggiven") != original, "first login uses the datapack's grant marker");
        CriteriaTriggers.TICK.trigger(player);
        for (var load : functions.getTag(id("minecraft:load"))) functions.execute(load, functions.getGameLoopSender());
        functions.execute(loop, functions.getGameLoopSender());
        require(player.getAdvancements().getOrStartProgress(server.getAdvancements().getAdvancement(terramity)).isDone(), "Terramity root still completes");
        require(player.getAdvancements().getOrStartProgress(server.getAdvancements().getAdvancement(crystalcraft)).isDone(), "Crystalcraft root still completes");
        require(player.getRecipeBook().contains(id("crystalcraft_unlimited_java:obsidian_shard_recipe")), "Crystalcraft still unlocks recipes");
        require(has(player, isTerramityGuide) == original, "Terramity auto grant matches expected behavior");
        require(has(player, stack -> stack.is(crystalopedia)) == original, "Crystalopedia auto grant matches expected behavior");
        require(has(player, isLogicraftGuide) == original, "Logicraft auto grant matches expected behavior");
        require(server.getRecipeManager().byKey(id("terramity:terramity_guidebook_recipe")).isPresent(), "Terramity crafting recipe remains");
        require(server.getRecipeManager().byKey(id("crystalcraft_unlimited_java:crystal_codex")).isPresent(), "Crystalopedia crafting recipe remains");

        player.getInventory().clearContent();
        require(player.addItem(new ItemStack(Items.KNOWLEDGE_BOOK)), "crafting fixture inserted");
        player.awardRecipes(List.of(guideRecipe));
        require(has(player, isLogicraftGuide), "manual Logicraft recipe still grants its written book");
        require(!has(player, stack -> stack.is(Items.KNOWLEDGE_BOOK)), "manual recipe consumes the placeholder");
        ForgeEventFactory.firePlayerLoggedIn(player);
        require(has(player, isLogicraftGuide), "later login keeps the existing guide");
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static boolean has(ServerPlayer player, Predicate<ItemStack> predicate) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (predicate.test(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private static void require(boolean result, String description) {
        if (!result) throw new AssertionError(description);
    }
}
