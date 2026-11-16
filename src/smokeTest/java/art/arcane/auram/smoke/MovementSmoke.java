package art.arcane.auram.smoke;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public final class MovementSmoke {
    private static final BlockPos HOME = new BlockPos(8, 81, 8);
    private static final BlockPos STALE = new BlockPos(160008, 81, 160008);

    public static void run(MinecraftServer server) throws Exception {
        ServerLevel home = server.overworld();
        home.getChunk(0, 0);
        ArmorStand entity = new ArmorStand(home, 8.5, 82, 8.5);
        SupportProbe probe = (SupportProbe) entity;
        ChunkPos missing = new ChunkPos(STALE);
        PatchSmokeMod.forbiddenChunkLookups.add(missing.toLong());
        try {
            probe.auramSmoke$setSupport(Optional.of(STALE));
            check(probe.auramSmoke$getOnPos(0.2F).equals(new BlockPos(8, 81, 8)), "unavailable support falls back to current coordinates");
            check(probe.auramSmoke$getSupport().isEmpty(), "unavailable contact is discarded");
            probe.auramSmoke$setSupport(Optional.of(STALE));
            entity.absMoveTo(10.5, 82, 8.5);
            check(probe.auramSmoke$getSupport().isEmpty(), "absolute relocation invalidates support");
            probe.auramSmoke$setSupport(Optional.of(STALE));
            entity.moveTo(8.5, 82, 8.5, 0, 0);
            check(probe.auramSmoke$getSupport().isEmpty(), "moveTo relocation invalidates support");
            ServerLevel nether = server.getLevel(Level.NETHER);
            probe.auramSmoke$setSupport(Optional.of(STALE));
            probe.auramSmoke$setLevel(nether);
            check(probe.auramSmoke$getSupport().isEmpty(), "level changes invalidate support");
            probe.auramSmoke$setLevel(home);
        } finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }

        // Ordinary setPos/move must retain valid support contacts. Legacy offsets
        // preserve fence collision height and the half-height slab behavior.
        home.setBlock(HOME, Blocks.OAK_FENCE.defaultBlockState(), 16);
        entity.setPos(8.5, 82.5, 8.5);
        probe.auramSmoke$setSupport(Optional.of(HOME));
        check(probe.auramSmoke$getOnPos(0.2F).equals(HOME), "fence support uses the original block Y");
        check(probe.auramSmoke$getSupport().equals(Optional.of(HOME)), "valid contact survives reads");
        entity.setPos(8.6, 82.5, 8.5);
        check(probe.auramSmoke$getSupport().equals(Optional.of(HOME)), "ordinary movement does not clear valid contacts");
        home.setBlock(HOME, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 16);
        entity.setPos(8.5, 81.5, 8.5);
        probe.auramSmoke$setSupport(Optional.of(HOME));
        check(probe.auramSmoke$getOnPos(0.2F).equals(HOME), "slab support height is preserved");

        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.enchant(Enchantments.SOUL_SPEED, 3);
        entity.setItemSlot(EquipmentSlot.FEET, boots);
        home.setBlock(HOME, Blocks.SOUL_SAND.defaultBlockState(), 16);
        entity.setPos(8.5, 82, 8.5);
        probe.auramSmoke$setSupport(Optional.of(HOME));
        Method changed = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_5806_", BlockPos.class);
        double originalSpeed = entity.getAttributeValue(Attributes.MOVEMENT_SPEED);
        changed.invoke(entity, HOME);
        check(entity.getAttributeValue(Attributes.MOVEMENT_SPEED) > originalSpeed, "Soul Speed is added on soul sand");
        home.setBlock(HOME, Blocks.STONE.defaultBlockState(), 16);
        changed.invoke(entity, HOME);
        check(Math.abs(entity.getAttributeValue(Attributes.MOVEMENT_SPEED) - originalSpeed) < 1e-8, "Soul Speed is removed off soul sand");

        BlockPos water = new BlockPos(10, 81, 8);
        home.setBlock(water.below(), Blocks.STONE.defaultBlockState(), 16);
        home.setBlock(water, Blocks.WATER.defaultBlockState(), 16);
        entity.setPos(8.5, 82, 8.5);
        entity.setOnGround(true);
        probe.auramSmoke$setSupport(Optional.of(HOME));
        boots = new ItemStack(Items.DIAMOND_BOOTS);
        boots.enchant(Enchantments.FROST_WALKER, 2);
        entity.setItemSlot(EquipmentSlot.FEET, boots);
        changed.invoke(entity, HOME.above());
        check(home.getBlockState(water).is(Blocks.FROSTED_ICE), "Frost Walker callback remains active");

        ServerPlayer player = new ServerPlayer(server, home, new GameProfile(UUID.randomUUID(), "AuramSmoke"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) { }
        };
        player.moveTo(8.5, 82, 8.5, 0, 0);
        home.addNewPlayer(player);
        SupportProbe playerProbe = (SupportProbe) player;
        // Exercise the real ServerPlayer command/home path and its actual network
        // teleport implementation; Forge FakePlayer overrides teleport with a no-op.
        for (Level target : new Level[]{server.getLevel(Level.NETHER), home, server.getLevel(Level.END), home, home}) {
            playerProbe.auramSmoke$setSupport(Optional.of(STALE));
            PatchSmokeMod.forbiddenChunkLookups.add(missing.toLong());
            try {
                player.teleportTo((ServerLevel) target, 8.5, 82, 8.5, 0, 0);
                check(player.level() == target && player.position().distanceTo(new Vec3(8.5, 82, 8.5)) < 1e-6, "real command teleport arrives in target world");
                check(playerProbe.auramSmoke$getSupport().isEmpty(), "command transfer clears old support");
            } finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }
        }

        ServerLevel nether = server.getLevel(Level.NETHER);
        portal(nether, new BlockPos(8, 81, 8));
        portal(home, new BlockPos(64, 81, 64));
        player.teleportTo(nether, 9.5, 82, 8.5, 0, 0);
        player.handleInsidePortal(new BlockPos(9, 82, 8));
        playerProbe.auramSmoke$setSupport(Optional.of(STALE));
        PatchSmokeMod.forbiddenChunkLookups.add(missing.toLong());
        try {
            player.changeDimension(home);
            check(player.level() == home, "vanilla Nether portal transfer returns to overworld");
            check(playerProbe.auramSmoke$getSupport().isEmpty(), "portal transfer clears old support");
            check(player.position().distanceTo(new Vec3(65.5, 82, 64.5)) < 8, "vanilla portal search selected the constructed return portal");
        } finally { PatchSmokeMod.forbiddenChunkLookups.clear(); }
        player.discard();
        LogUtils.getLogger().info("[AURAM MOVEMENT] PASS unavailable support, absolute moves, level changes, fences/slabs, Soul Speed, Frost Walker, real Nether/End/home teleports and vanilla Nether portal return");
    }

    private static void portal(ServerLevel level, BlockPos bottom) {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
                level.setBlock(bottom.offset(x, y, 0), frame ? Blocks.OBSIDIAN.defaultBlockState()
                        : Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
