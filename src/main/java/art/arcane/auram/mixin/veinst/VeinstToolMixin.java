package art.arcane.auram.mixin.veinst;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.mcreator.oreminer.procedures.IsSneakingProcedure", remap = false)
public abstract class VeinstToolMixin {
    @Unique
    private static final String AURAM_EXECUTE =
            "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDD"
                    + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/Entity;)V";

    @Unique
    private static final TagKey<Item> AURAM_PAXELS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("auram", "veinst_paxels"));

    @Unique
    private static final TagKey<Item> AURAM_TINKERS_STONE_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("tconstruct", "modifiable/harvest/stone"));

    @ModifyExpressionValue(method = AURAM_EXECUTE, at = @At(value = "FIELD", target =
            "Lnet/minecraft/world/item/Items;IRON_PICKAXE:Lnet/minecraft/world/item/Item;", remap = true), require = 1)
    private static Item auram$allowMiningTools(Item vanillaPickaxe, @Local(argsOnly = true) Entity miner,
                                               @Local(argsOnly = true) LevelAccessor level,
                                               @Local(argsOnly = true) BlockState ore,
                                               @Share("auram$veinstTool") LocalRef<ItemStack> tool) {
        if (miner instanceof ServerPlayer player && player.level() == level) {
            ItemStack held = player.getMainHandItem();
            if ((held.is(AURAM_PAXELS) || (held.is(ItemTags.PICKAXES) && held.is(AURAM_TINKERS_STONE_TOOLS)))
                    && held.isCorrectToolForDrops(ore)) {
                tool.set(held);
                // Only change the expected item in Veinst's equality check.
                return held.getItem();
            }
        }
        return vanillaPickaxe;
    }

    @WrapOperation(method = AURAM_EXECUTE, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/Block;dropResources(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;)V", remap = true), require = 1)
    private static void auram$leaveDropsToActualTool(BlockState state, LevelAccessor level, BlockPos pos,
                                                    BlockEntity entity, Operation<Void> original,
                                                    @Share("auram$veinstTool") LocalRef<ItemStack> tool) {
        if (tool.get() == null) original.call(state, level, pos, entity);
    }

    @WrapOperation(method = AURAM_EXECUTE, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelAccessor;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            remap = true), require = 1)
    private static boolean auram$harvestWithActualTool(LevelAccessor level, BlockPos pos, boolean drops,
                                                      Operation<Boolean> original, @Local(argsOnly = true) Entity miner,
                                                      @Share("auram$veinstTool") LocalRef<ItemStack> tool) {
        ItemStack held = tool.get();
        if (held == null) return original.call(level, pos, drops);
        ServerPlayer player = (ServerPlayer) miner;
        // Use the player's break path for Forge protection, loot and tool-specific durability.
        return player.getMainHandItem() == held && player.serverLevel().mayInteract(player, pos)
                && held.isCorrectToolForDrops(level.getBlockState(pos))
                && player.gameMode.destroyBlock(pos);
    }

    @WrapOperation(method = AURAM_EXECUTE, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/ItemStack;hurt(ILnet/minecraft/util/RandomSource;"
                    + "Lnet/minecraft/server/level/ServerPlayer;)Z", remap = true), require = 1)
    private static boolean auram$avoidDoubleDamage(ItemStack stack, int damage, RandomSource random, ServerPlayer player,
                                                   Operation<Boolean> original,
                                                   @Share("auram$veinstTool") LocalRef<ItemStack> tool) {
        return tool.get() == null && original.call(stack, damage, random, player);
    }
}
