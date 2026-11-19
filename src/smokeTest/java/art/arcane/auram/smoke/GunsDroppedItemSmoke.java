package art.arcane.auram.smoke;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact item/fire selector parity plus the installed Guns conversion and projectile loop. */
public final class GunsDroppedItemSmoke {
    static int prepare(ServerPlayer player, CommandSourceStack source) throws Exception {
        ServerLevel level = player.serverLevel();
        List<ItemEntity> entities = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ItemStack stack = new ItemStack(i % 2 == 0 ? Items.CROSSBOW : Items.CLOCK);
            if (i % 3 != 0) stack.getOrCreateTag().putInt("CustomModelData", i % 5 == 0 ? 88918 : i);
            ItemEntity item = new ItemEntity(level, 8.5 + i % 4, -61, 8.5 + i / 4 % 4, stack);
            item.setRemainingFireTicks(i % 2 == 0 ? 149 : 150);
            item.setNeverPickUp();
            item.setNoGravity(true);
            item.addTag("auram_item_fixture");
            level.addFreshEntity(item);
            entities.add(item);
        }
        ItemEntity custom = new ItemEntity(level, 0, -61, 0, new ItemStack(Items.CROSSBOW)) {
            @Override public void addAdditionalSaveData(CompoundTag tag) {
                super.addAdditionalSaveData(tag);
                tag.put("Item", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
            }
        };
        entities.add(custom);
        entities.add(new ItemEntity(level, 0, -61, 0, ItemStack.EMPTY));

        int comparisons = 0;
        for (String nbt : List.of("{Item:{tag:{CustomModelData:88918}}}",
                "{Item:{id:\"minecraft:crossbow\",Count:1b}}", "{Item:{id:\"minecraft:diamond\"}}",
                "{Item:{Count:1}}", "{Item:{tag:{}}}", "{Item:{tag:\"wrong\"}}",
                "{Item:{}}", "{Item:{id:\"minecraft:clock\",tag:{CustomModelData:5}}}",
                "{Item:{tag:{CustomModelData:88918}},Fire:149s}", "{Item:{tag:{CustomModelData:88918}},Fire:150s}",
                "{Item:{tag:{}},Fire:149}", "{Item:{tag:{}},Fire:149s,Age:0s}", "{Item:{ForgeCaps:{}}}")) {
            CompoundTag expected = TagParser.parseTag(nbt);
            for (boolean invert : new boolean[]{false, true}) {
                var selector = new EntitySelectorParser(new StringReader("@s[nbt=" + (invert ? "!" : "") + nbt + "]")).parse();
                for (ItemEntity item : entities) {
                    boolean reference = NbtUtils.compareNbt(expected, item.saveWithoutId(new CompoundTag()), true) != invert;
                    check(!selector.findEntities(source.withEntity(item)).isEmpty() == reference, nbt + " inverted=" + invert);
                    comparisons++;
                }
            }
        }
        return comparisons;
    }

    static Map<String,Object> exercise(ServerPlayer player, CommandSourceStack source) {
        ServerLevel level = player.serverLevel();
        level.getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(8, -61, 8)).inflate(5),
                item -> item.getTags().contains("auram_item_fixture")).forEach(ItemEntity::discard);
        BlockPos rod = new BlockPos(4, -62, 4);
        level.setBlockAndUpdate(rod, Blocks.LIGHTNING_ROD.defaultBlockState());
        ItemEntity charging = onRod(level, rod, 150);
        AreaEffectCloud projectile = new AreaEffectCloud(level, 2.5, -60, 2.5);
        projectile.addTag("gz_projectile");
        projectile.addTag("gz_bullet_0");
        level.addFreshEntity(projectile);
        var manager = player.server.getFunctions();
        var loop = manager.get(ResourceLocation.parse("ggunz:loop")).orElseThrow();
        int result = manager.execute(loop, source);
        check(charging.isInvulnerable(), "lightning rod marks the charging item invulnerable");
        check(Math.abs(projectile.getZ() - 3.0) < 1.0e-6 && !projectile.isRemoved(), "installed projectile loop advances its cloud");
        charging.setRemainingFireTicks(149);
        int cleanupCommands = manager.execute(loop, source);
        check(charging.isRemoved(), "spent item is consumed");
        int bullets = level.getScoreboard().getOrCreatePlayerScore(projectile.getScoreboardName(),
                level.getScoreboard().getObjective("gz_bullets")).getScore();
        List<String> stackIds = level.getEntitiesOfClass(ItemEntity.class, new AABB(rod).inflate(1.1)).stream()
                .map(item -> item.getItem().save(new CompoundTag()).toString()).sorted().toList();
        check(!stackIds.isEmpty(), "lightning rod conversion spawned the gun");
        return Map.of("commands", result, "cleanup_commands", cleanupCommands,
                "consumed", charging.isRemoved(), "bullet_age", bullets, "remaining_stacks", stackIds);
    }

    private static ItemEntity onRod(ServerLevel level, BlockPos rod, int fireTicks) {
        ItemStack stack = new ItemStack(Items.CROSSBOW);
        stack.getOrCreateTag().putInt("CustomModelData", 88918);
        ItemEntity item = new ItemEntity(level, rod.getX()+0.5, rod.getY()+1, rod.getZ()+0.5, stack);
        item.setRemainingFireTicks(fireTicks);
        level.addFreshEntity(item);
        return item;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
