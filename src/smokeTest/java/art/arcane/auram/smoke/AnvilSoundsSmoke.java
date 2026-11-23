package art.arcane.auram.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = "auram_patch_smoke", value = Dist.CLIENT)
public final class AnvilSoundsSmoke {
    private static AnvilSoundsSmoke active;

    private final Minecraft client;
    private final BlockPos anvil;
    private final BlockPos nearby;
    private final CompletableFuture<Void> result = new CompletableFuture<>();
    private final Sounds sounds = new Sounds();
    private Stage stage = Stage.PLACED;
    private volatile String serverMenuState = "inputs not sent";
    private int ticks;

    private enum Stage { PLACED, OPENED, BOOK, BOOK_SOUND, REPAIR, REPAIR_SOUND, RENAME, RENAME_SOUND, INVALID, ABANDONED, FINISHED }

    private AnvilSoundsSmoke(Minecraft client) {
        this.client = client;
        anvil = client.player.blockPosition().east(2);
        nearby = anvil.south(2);
        MinecraftForge.EVENT_BUS.register(sounds);
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            player.serverLevel().setBlockAndUpdate(anvil.below(), Blocks.STONE.defaultBlockState());
            player.serverLevel().setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
        });
    }

    static CompletableFuture<Void> start(Minecraft client) {
        active = new AnvilSoundsSmoke(client);
        return active.result;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (active == null || event.phase != TickEvent.Phase.END) return;
        AnvilSoundsSmoke fixture = active;
        try {
            if (++fixture.ticks > 500) {
                throw new AssertionError("Anvil test timed out at " + fixture.stage + ". Client "
                        + describe(fixture.client.player.containerMenu) + ". Server " + fixture.serverMenuState);
            }
            fixture.advance();
        } catch (Throwable failure) {
            fixture.finish(failure);
        }
    }

    private void advance() throws Exception {
        switch (stage) {
            case PLACED -> {
                if (client.level.getBlockState(anvil).is(Blocks.ANVIL)) {
                    var hit = new BlockHitResult(Vec3.atCenterOf(anvil), Direction.UP, anvil, false);
                    client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
                    stage = Stage.OPENED;
                }
            }
            case OPENED -> {
                if (client.player.containerMenu instanceof AnvilMenu) {
                    loadInputs(new ItemStack(Items.DIAMOND_SWORD), EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(Enchantments.SHARPNESS, 1)));
                    stage = Stage.BOOK;
                }
            }
            case BOOK -> {
                if (canTake()) {
                    take(ClickType.QUICK_MOVE);
                    client.level.levelEvent(1030, nearby, 0);
                    stage = Stage.BOOK_SOUND;
                }
            }
            case BOOK_SOUND -> {
                if (sounds.ids.size() >= 2) {
                    requireSounds(SoundEvents.ANVIL_USE.getLocation(), SoundEvents.ENCHANTMENT_TABLE_USE.getLocation());
                    sounds.ids.clear();
                    ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
                    damaged.setDamageValue(150);
                    loadInputs(damaged, new ItemStack(Items.IRON_INGOT));
                    stage = Stage.REPAIR;
                }
            }
            case REPAIR -> {
                if (canTake()) {
                    take(ClickType.PICKUP);
                    stage = Stage.REPAIR_SOUND;
                }
            }
            case REPAIR_SOUND -> {
                if (!sounds.ids.isEmpty()) {
                    requireSounds(SoundEvents.ANVIL_USE.getLocation());
                    sounds.ids.clear();
                    loadInputs(new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY, "Anvil QA");
                    stage = Stage.RENAME;
                }
            }
            case RENAME -> {
                if (canTake() && client.player.containerMenu.getSlot(2).getItem().hasCustomHoverName()) {
                    take(ClickType.PICKUP);
                    stage = Stage.RENAME_SOUND;
                }
            }
            case RENAME_SOUND -> {
                if (!sounds.ids.isEmpty()) {
                    requireSounds(SoundEvents.ANVIL_USE.getLocation());
                    sounds.ids.clear();
                    loadInputs(ItemStack.EMPTY, EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(Enchantments.SHARPNESS, 1)));
                    stage = Stage.INVALID;
                }
            }
            case INVALID -> {
                var menu = (AnvilMenu) client.player.containerMenu;
                if (!menu.getSlot(2).hasItem() && menu.getSlot(1).getItem().is(Items.ENCHANTED_BOOK)) {
                    take(ClickType.PICKUP);
                    client.level.levelEvent(1030, anvil, 0);
                    requireSounds(SoundEvents.ANVIL_USE.getLocation());
                    sounds.ids.clear();
                    loadInputs(new ItemStack(Items.DIAMOND_SWORD), EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(Enchantments.SHARPNESS, 1)));
                    stage = Stage.ABANDONED;
                }
            }
            case ABANDONED -> {
                if (canTake()) {
                    client.player.closeContainer();
                    client.level.levelEvent(1030, anvil, 0);
                    requireSounds(SoundEvents.ANVIL_USE.getLocation());
                    Files.writeString(Path.of("auram-anvil-sounds-smoke.txt"),
                            "Passed book quick move, neighboring use, repair, rename, invalid and abandoned result\n");
                    finish(null);
                }
            }
            case FINISHED -> { }
        }
    }

    private void loadInputs(ItemStack left, ItemStack right) {
        loadInputs(left, right, "");
    }

    private void loadInputs(ItemStack left, ItemStack right, String name) {
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (!(player.containerMenu instanceof AnvilMenu menu)) {
                serverMenuState = describe(player.containerMenu);
                return;
            }
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(left);
            menu.getSlot(1).set(right);
            menu.setItemName(name);
            menu.createResult();
            menu.broadcastChanges();
            serverMenuState = describe(menu);
        });
    }

    private static String describe(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (!(menu instanceof AnvilMenu anvil)) return menu.getClass().getName();
        return "anvil inputs " + anvil.getSlot(0).getItem() + " and " + anvil.getSlot(1).getItem()
                + ", output " + anvil.getSlot(2).getItem() + ", cost " + anvil.getCost();
    }

    private boolean canTake() {
        return client.player.containerMenu instanceof AnvilMenu menu
                && menu.getSlot(2).hasItem() && menu.getSlot(2).mayPickup(client.player);
    }

    private void take(ClickType click) {
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, 2, 0, click, client.player);
    }

    private void requireSounds(ResourceLocation... expected) {
        if (!sounds.ids.equals(List.of(expected))) {
            throw new AssertionError("Wanted " + List.of(expected) + " but got " + sounds.ids);
        }
    }

    private void finish(Throwable failure) {
        stage = Stage.FINISHED;
        active = null;
        MinecraftForge.EVENT_BUS.unregister(sounds);
        if (failure == null) result.complete(null);
        else result.completeExceptionally(failure);
    }

    public static final class Sounds {
        private final List<ResourceLocation> ids = new ArrayList<>();

        @SubscribeEvent
        public void onSound(PlaySoundEvent event) {
            var id = event.getOriginalSound().getLocation();
            if (id.equals(SoundEvents.ANVIL_USE.getLocation()) || id.equals(SoundEvents.ENCHANTMENT_TABLE_USE.getLocation())) {
                ids.add(id);
            }
        }
    }
}
