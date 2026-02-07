package art.arcane.auram.util;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = Auram.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class OreChiselHandler {
    @SubscribeEvent
    public static void onOreChisel(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (level.isClientSide) {
            if (isValidChiselTarget(stack, state)) {
                player.swing(event.getHand());
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true); 
            }
            return;
        }

        if (!isValidChiselTarget(stack, state)) {
            return;
        }

        if (!stack.isCorrectToolForDrops(state)) {
            return;
        }
        
        if(player.getCooldowns().isOnCooldown(stack.getItem())){
            return;
        }
        
        Optional<Map.Entry<Item, Block>> o = Auram.ROCK_ITEM_TO_ORE_BLOCK.entrySet().stream().filter((e) -> e.getValue() == state.getBlock()).findFirst();
        
        if(o.isEmpty()) {
            return;
        }

        int lvl = stack.getEnchantmentLevel(Auram.CHISEL.get());
        
        if(lvl <= 0) {
            return;
        }
        
        int chlvl = stack.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE);
        int bonus = chlvl > 0 ? (int)Math.round(Math.random() * chlvl) : 0;
        float chance = Math.max(0.1f, Math.min(1f, 1f/(lvl+1)));
        level.playSound(null, pos, SoundEvents.TUFF_STEP, SoundSource.BLOCKS, 0.7F, 0.5F + (float)Math.random());
        popResource(level, player.getOnPos(), new ItemStack(o.get().getKey(), (int)Math.ceil(Math.random() * ((o.get().getKey().toString().contains("deepslate") ? 6 : 4) + bonus))));
        player.getCooldowns().addCooldown(stack.getItem(), lvl == 1 ? 20 : lvl == 2 ? 16 : lvl == 3 ? 12 : lvl == 4 ? 8 : lvl == 5 ? 2 : 1);
        
        if (level.random.nextFloat() < chance) {
            level.destroyBlock(pos, false);
            level.playSound(null, pos, SoundEvents.TUFF_BREAK, SoundSource.BLOCKS, 0.7F, 0.3F + (float)Math.random());
        } 
        
        stack.hurtAndBreak((10-lvl)*(chlvl+1), player, (p) -> p.broadcastBreakEvent(event.getHand()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static boolean isValidChiselTarget(ItemStack stack, BlockState state) {
        return stack.is(ItemTags.PICKAXES) && Auram.ROCK_ITEM_TO_ORE_BLOCK.values().stream().anyMatch((b) -> b == state.getBlock());
    }

    private static void popResource(Level level, BlockPos pos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, 
            pos.getX() + 0.5, 
            pos.getY() + 1.1, 
            pos.getZ() + 0.5, 
            stack);
        entity.setPickUpDelay(0);
        level.addFreshEntity(entity);
    }
}