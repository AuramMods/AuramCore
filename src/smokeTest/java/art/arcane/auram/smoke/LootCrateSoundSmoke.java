package art.arcane.auram.smoke;

import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Invokes the installed crate item, observing the server's actual sound event. */
public final class LootCrateSoundSmoke {
    @SuppressWarnings("unchecked")
    public static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var player = FakePlayerFactory.getMinecraft(level);
        Class<?> fileType = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
        Object file = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile")
                .getConstructor(MinecraftServer.class).newInstance(server);
        Class<?> tableType = Class.forName("dev.ftb.mods.ftbquests.quest.loot.RewardTable");
        Object table = tableType.getConstructor(long.class, fileType).newInstance(104729L, file);
        Class<?> crateType = Class.forName("dev.ftb.mods.ftbquests.quest.loot.LootCrate");
        Object crate = crateType.getConstructor(tableType, boolean.class).newInstance(table, false);
        var crateField = crateType.getField("LOOT_CRATES");
        Map<String, Object> previous = (Map<String, Object>) crateField.get(null);
        Map<String, Object> crates = new HashMap<>(previous);
        crates.put("auram_sound_smoke", crate);
        List<PlayLevelSoundEvent.AtPosition> sounds = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == level) sounds.add(event);
        };
        crateField.set(null, crates);
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("ftbquests:lootcrate"));
            for (boolean crouching : new boolean[]{false, true}) {
                ItemStack stack = new ItemStack(item, 4);
                stack.getOrCreateTag().putString("type", "auram_sound_smoke");
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.setPose(crouching ? Pose.CROUCHING : Pose.STANDING);
                sounds.clear();
                var result = item.use(level, player, InteractionHand.MAIN_HAND);
                require(result.getResult() == InteractionResult.SUCCESS, "Crate opening succeeds");
                require(stack.getCount() == (crouching ? 0 : 3), "Opening consumes the expected crate count");
                require(sounds.size() == 1, "One opening sound per right-click");
                var sound = sounds.get(0);
                require(sound.getSound().value() == SoundEvents.ITEM_PICKUP, "Uses item pickup sound");
                require(sound.getOriginalPitch() == 0.5F, "Uses exactly 0.5 pitch");
                require(sound.getOriginalVolume() == 0.8F, "Preserves FTB's volume");
                require(sound.getSource() == SoundSource.PLAYERS, "Preserves the sound category");
            }
            sounds.clear();
            level.playSound(null, 0, 64, 0, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
            require(sounds.size() == 1 && sounds.get(0).getSound().value() == SoundEvents.ITEM_BREAK
                    && sounds.get(0).getOriginalPitch() == 1.0F, "Other item-break sounds are unchanged");
            System.out.println("QA_EVT {\"event\":\"loot-crate-sound\",\"status\":\"pass\",\"details\":\"Normal and sneak opening use item pickup at pitch 0.5; unrelated item break unchanged\"}");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            crateField.set(null, previous);
            player.setPose(Pose.STANDING);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
