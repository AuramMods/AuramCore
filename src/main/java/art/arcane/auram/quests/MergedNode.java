package art.arcane.auram.quests;

import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

class MergedNode {
    String id;
    int depth;
    List<Recipe<?>> recipes = new ArrayList<>();
    
    public MergedNode(String id, int depth) { 
        this.id = id; 
        this.depth = depth;
    }
}