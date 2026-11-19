package art.arcane.auram.smoke;

import com.google.gson.JsonParser;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Replays the installed guns functions and compares the exact selector forms on live inventory. */
public final class GunsOffhandSmoke {
    private static final int OFFHAND = 40;
    private static final String PATH = "predicates/offhand/";

    static Map<String, Object> run(ServerPlayer player, CommandSourceStack source, List<String> actionBars) throws Exception {
        int comparisons = Boolean.getBoolean("auram.smoke.gunsOffhandCandidate") ? comparePredicates(player, source) : 0;
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.selected = 0;

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (String nbt : List.of("{gz_data:{sniper:1}}", "{gz_data:{sniper:1b}}", "{CustomModelData:88920}", "{}")) {
            ItemStack gun = new ItemStack(Items.CROSSBOW);
            gun.setTag(TagParser.parseTag(nbt));
            for (int slot : new int[]{OFFHAND, 0}) {
                inventory.setItem(0, ItemStack.EMPTY);
                inventory.setItem(OFFHAND, ItemStack.EMPTY);
                inventory.setItem(slot, gun);
                player.addTag("scoping");
                player.addTag("rpgscoping");
                actionBars.clear();
                int result = execute(source, "ggunz:utilities/scopes");
                snapshots.add(Map.of("offhand", slot == OFFHAND, "tag", nbt, "result", result,
                        "sniper", player.getTags().contains("scoping"), "rpg", player.getTags().contains("rpgscoping"),
                        "action_bars", List.copyOf(actionBars)));
            }
        }
        check((Boolean) snapshots.get(0).get("sniper"), "sniper in offhand qualifies");
        check(!(Boolean) snapshots.get(1).get("sniper"), "sniper in main hand does not qualify");
        check(!(Boolean) snapshots.get(2).get("sniper"), "byte and int NBT are distinct");
        check((Boolean) snapshots.get(4).get("rpg"), "RPG scope works in offhand");

        ItemStack reloading = new ItemStack(Items.CROSSBOW);
        reloading.getOrCreateTag().putInt("CustomModelData", 88889);
        inventory.setItem(OFFHAND, reloading);
        actionBars.clear();
        var scoreboard = player.getScoreboard();
        var reload = scoreboard.getObjective("gz_reload");
        check(reload != null, "Guns load function defines the reload objective");
        var score = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), reload);
        score.setScore(0);
        int reloadCommands = execute(source, "ggunz:reload/timer");
        check(score.getScore() == 1, "reload timer advances the score");
        return Map.of("comparisons", comparisons, "scope_results", snapshots,
                "reload_result", reloadCommands, "reload_score", score.getScore(),
                "reload_action_bars", List.copyOf(actionBars));
    }

    private static int comparePredicates(ServerPlayer player, CommandSourceStack source) throws Exception {
        var resources = player.server.getResourceManager().listResources("predicates/offhand",
                name -> name.getNamespace().equals("ggunz") && name.getPath().endsWith(".json"));
        check(resources.size() == 33, "all 33 offhand predicates loaded");
        var inventory = player.getInventory();
        inventory.selected = 0;
        int comparisons = 0;
        for (var entry : resources.entrySet()) {
            String key = entry.getKey().getPath();
            String id = "ggunz:offhand/" + key.substring(PATH.length(), key.length() - ".json".length());
            String nbt;
            try (var reader = entry.getValue().openAsReader()) {
                nbt = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("predicate")
                        .getAsJsonObject("equipment").getAsJsonObject("offhand").get("nbt").getAsString();
            }
            CompoundTag expected = TagParser.parseTag(nbt);
            EntitySelector oldPositive = selector("@a[nbt={Inventory:[{Slot:-106b,tag:" + nbt + "}]}]");
            EntitySelector oldNegative = selector("@a[nbt=!{Inventory:[{Slot:-106b,tag:" + nbt + "}]}]");
            EntitySelector newPositive = selector("@a[predicate=" + id + "]");
            EntitySelector newNegative = selector("@a[predicate=!" + id + "]");
            for (int state = 0; state < 7; state++) {
                inventory.clearContent();
                ItemStack stack = new ItemStack(Items.CROSSBOW);
                switch (state) {
                    case 0 -> { }
                    case 1 -> inventory.setItem(OFFHAND, stack);
                    case 2 -> {
                        stack.setTag(expected.copy());
                        inventory.setItem(0, stack);
                    }
                    case 3 -> {
                        stack.setTag(expected.copy());
                        inventory.setItem(OFFHAND, stack);
                    }
                    case 4 -> {
                        stack.setTag(expected.copy());
                        stack.getOrCreateTag().putInt("Ammo", 7);
                        inventory.setItem(OFFHAND, stack);
                    }
                    case 5 -> {
                        stack.setTag(expected.copy());
                        stack.setCount(0);
                        inventory.setItem(OFFHAND, stack);
                    }
                    case 6 -> {
                        stack.setTag(expected.copy());
                        inventory.setItem(OFFHAND, stack);
                        for (String root : expected.getAllKeys()) stack.getOrCreateTag().putString(root, "different");
                    }
                    default -> throw new AssertionError(state);
                }
                check(oldPositive.findPlayers(source).equals(newPositive.findPlayers(source)), "positive " + id + " state " + state);
                check(oldNegative.findPlayers(source).equals(newNegative.findPlayers(source)), "inverted " + id + " state " + state);
                check(newPositive.findPlayers(source).contains(player) == (state == 3 || state == 4),
                        "actual offhand predicate does not match when it should");
                comparisons += 2;
            }
        }
        return comparisons;
    }

    private static EntitySelector selector(String command) throws Exception {
        return new EntitySelectorParser(new StringReader(command)).parse();
    }

    private static int execute(CommandSourceStack source, String id) {
        var functions = source.getServer().getFunctions();
        return functions.execute(functions.get(ResourceLocation.parse(id)).orElseThrow(), source);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
