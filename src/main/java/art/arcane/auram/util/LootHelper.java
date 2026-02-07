package art.arcane.auram.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class LootHelper {
    public static ItemStack getMaxedFakeDrop(Block block, ServerLevel level) {
        boolean moreThanOne = false;
        
       for(int i = 0; i < 10; i++) {
           if(getFakeDrop(block, level).getCount() > 1) {
               moreThanOne = true;
               break;
           }
       }
       
       int tc = 0;
       int hc = 0;
       if(moreThanOne) {
           while(tc++ < 100) {
               int c = getFakeDrop(block, level).getCount();
               if(c > hc) {
                   hc = c;
               }
           }
           
           ItemStack s = getFakeDrop(block, level);
           s.setCount(hc);
           return s;
       }
       
       return getFakeDrop(block, level);
    }
    
    public static ItemStack getFakeDrop(Block block, ServerLevel level) {
        BlockState state = block.defaultBlockState();
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.TOOL, tool);
        List<ItemStack> drops = state.getDrops(params);

        if (drops.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        for (ItemStack drop : drops) {
            if (drop.getItem() != block.asItem()) {
                return drop.copy();
            }
        }

        return drops.get(0).copy();
    }
}