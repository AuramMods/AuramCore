package art.arcane.auram.client;

import art.arcane.auram.tweak.TweakConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "auram", value = Dist.CLIENT)
public final class AnvilSounds {
    private static final Settings SETTINGS = TweakConfig.read("anvil-sounds.json", Settings.class, new Settings());
    private static final long RESPONSE_TIMEOUT_MS = 5_000;

    private static ClientLevel level;
    private static BlockPos position;
    private static AnvilMenu menu;
    private static long openUntil;
    private static long soundUntil;

    private AnvilSounds() {
    }

    public static void requestOpen(ClientLevel world, BlockPos pos) {
        if (!SETTINGS.enabled) return;
        clearMenu();
        level = world;
        position = pos.immutable();
        openUntil = Util.getMillis() + RESPONSE_TIMEOUT_MS;
    }

    public static void openedMenu(Minecraft client) {
        menu = null;
        soundUntil = 0;
        if (client.level == level && Util.getMillis() <= openUntil && client.player != null
                && client.player.containerMenu instanceof AnvilMenu anvil) {
            menu = anvil;
        } else {
            clearMenu();
        }
        openUntil = 0;
    }

    public static void tookResult(AnvilMenu from) {
        if (menu != from) return;
        soundUntil = from.getSlot(1).getItem().is(Items.ENCHANTED_BOOK)
                ? Util.getMillis() + RESPONSE_TIMEOUT_MS : 0;
    }

    public static SoundEvent replacement(ClientLevel world, BlockPos pos, SoundEvent original) {
        if (original == SoundEvents.ANVIL_USE && world == level && soundUntil != 0
                && Util.getMillis() <= soundUntil && pos.equals(position) && isMenuOpen(Minecraft.getInstance())) {
            soundUntil = 0;
            return SoundEvents.ENCHANTMENT_TABLE_USE;
        }
        return original;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || (menu != null ? !isMenuOpen(client) : Util.getMillis() > openUntil)) {
            clearMenu();
        }
    }

    private static boolean isMenuOpen(Minecraft client) {
        return client.player != null && client.player.containerMenu == menu;
    }

    private static void clearMenu() {
        level = null;
        position = null;
        menu = null;
        openUntil = 0;
        soundUntil = 0;
    }

    private static final class Settings {
        private boolean enabled;
    }
}
