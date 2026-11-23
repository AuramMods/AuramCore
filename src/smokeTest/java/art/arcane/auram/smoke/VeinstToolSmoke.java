package art.arcane.auram.smoke;

import art.arcane.auram.Auram;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.UUID;

final class VeinstToolSmoke {
    private static final BlockPos ORE = new BlockPos(8, -61, 8);
    private static final BlockPos NEIGHBOR = ORE.east();

    static void run(MinecraftServer server) throws ReflectiveOperationException {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "AuramVeinst"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.moveTo(8.5, -62, 6.5, 0, 0);
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(true);

        boolean reference = Boolean.getBoolean("auram.smoke.veinstReference");
        tryTool(player, new ItemStack(Items.IRON_PICKAXE), Blocks.COAL_ORE, true);

        var mekanism = item("mekanismtools:diamond_paxel");
        mekanism.enchant(Enchantments.SILK_TOUCH, 1);
        var drops = new DiamondOreDrops();
        MinecraftForge.EVENT_BUS.register(drops);
        try {
            tryTool(player, mekanism, Blocks.DIAMOND_ORE, !reference);
            if (!reference) {
                require(mekanism.getDamageValue() == 2, "Mekanism paxel loses one durability per ore");
                require(drops.rockCount >= 2 && drops.rockCount <= 8 && drops.oreCount == 0,
                        "Auram's rock loot modifier handles both ore drops");
                boolean bypass = Auram.BYPASS_ROCK_GENERATION;
                Auram.BYPASS_ROCK_GENERATION = true;
                try {
                    tryTool(player, mekanism, Blocks.DIAMOND_ORE, true);
                    require(drops.oreCount == 2, "Silk Touch receives both ore blocks when the rock modifier is bypassed");
                } finally {
                    Auram.BYPASS_ROCK_GENERATION = bypass;
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(drops);
        }

        tryTool(player, item("evolvedmekanism:refined_redstone_paxel"), Blocks.COAL_ORE, !reference);
        tryTool(player, item("mysticalagradditions:supremium_paxel"), Blocks.COAL_ORE, !reference);
        tryTool(player, item("allthemodium:alloy_paxel"), Blocks.COAL_ORE, !reference);
        tryTool(player, item("gobber2:gobber2_paxel"), Blocks.COAL_ORE, !reference);

        var tinkers = buildTinkersPickaxe();
        tryTool(player, tinkers, Blocks.COAL_ORE, !reference);
        if (!reference) require(tinkers.getDamageValue() == 2, "Tinkers' pickaxe uses native durability");

        tryTool(player, item("mekanismtools:wood_paxel"), Blocks.DIAMOND_ORE, false);
        tryTool(player, new ItemStack(Items.DIAMOND_AXE), Blocks.COAL_ORE, false);
        player.setShiftKeyDown(false);
        tryTool(player, item("mekanismtools:diamond_paxel"), Blocks.COAL_ORE, false);
        player.setShiftKeyDown(true);

        if (!reference) {
            var protection = new ProtectedOre();
            MinecraftForge.EVENT_BUS.register(protection);
            try {
                placeVein(player, Blocks.COAL_ORE);
                clickVein(player, item("mekanismtools:diamond_paxel"));
                require(player.serverLevel().getBlockState(ORE).isAir(), "unprotected target is mined");
                require(player.serverLevel().getBlockState(NEIGHBOR).is(Blocks.COAL_ORE), "Forge-canceled ore stays intact");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(protection);
            }
        }
    }

    private static ItemStack item(String id) {
        var item = Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id)), id);
        require(item != Items.AIR, "missing fixture item " + id);
        return new ItemStack(item);
    }

    private static ItemStack buildTinkersPickaxe() throws ReflectiveOperationException {
        String library = "slimeknights.tconstruct.library.";
        Class<?> materialId = Class.forName(library + "materials.definition.MaterialId");
        Class<?> variant = Class.forName(library + "materials.definition.MaterialVariant");
        Object iron = materialId.getConstructor(String.class).newInstance("tconstruct:iron");
        Object ironVariant = variant.getMethod("of", materialId, String.class).invoke(null, iron, "");
        Class<?> modifiable = Class.forName(library + "tools.item.IModifiable");
        Class<?> builder = Class.forName(library + "tools.helper.ToolBuildHandler");
        return (ItemStack) builder.getMethod("createSingleMaterial", modifiable, variant)
                .invoke(null, item("tconstruct:pickaxe").getItem(), ironVariant);
    }

    private static void tryTool(ServerPlayer player, ItemStack stack, Block ore, boolean expected) {
        placeVein(player, ore);
        clickVein(player, stack);
        require(player.serverLevel().getBlockState(ORE).isAir() == expected
                && player.serverLevel().getBlockState(NEIGHBOR).isAir() == expected,
                ForgeRegistries.ITEMS.getKey(stack.getItem()) + " vein result should be " + expected);
    }

    private static void placeVein(ServerPlayer player, Block ore) {
        for (BlockPos pos : new BlockPos[]{ORE, NEIGHBOR}) {
            player.serverLevel().setBlockAndUpdate(pos, ore.defaultBlockState());
        }
    }

    private static void clickVein(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (LeftClickBlock.Action action : new LeftClickBlock.Action[]{LeftClickBlock.Action.START, LeftClickBlock.Action.STOP}) {
            MinecraftForge.EVENT_BUS.post(new LeftClickBlock(player, ORE, Direction.NORTH, action));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class DiamondOreDrops {
        private int rockCount;
        private int oreCount;

        @SubscribeEvent
        public void onDrop(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ItemEntity entity) {
                ItemStack drop = entity.getItem();
                if (drop.is(Items.DIAMOND_ORE)) oreCount += drop.getCount();
                if (ResourceLocation.tryBuild("auram", "diamond_rock").equals(ForgeRegistries.ITEMS.getKey(drop.getItem()))) {
                    rockCount += drop.getCount();
                }
            }
        }
    }

    public static final class ProtectedOre {
        @SubscribeEvent
        public void onBreak(BlockEvent.BreakEvent event) {
            if (event.getPos().equals(NEIGHBOR)) event.setCanceled(true);
        }
    }
}
