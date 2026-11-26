package art.arcane.auram.mixin.almostunified;

import art.arcane.auram.compat.almostunified.CraftingRecipeOutput;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.almostreliable.unified.recipe.RecipeLink", remap = false)
abstract class RecipeLinkOutputMixin implements CraftingRecipeOutput {
    @Shadow @Final private boolean isCraftingRecipe;

    @Shadow
    private Item getCraftingRecipeOutput() {
        throw new AssertionError();
    }

    @Override
    public boolean auram$hasCraftingOutputKey() {
        return isCraftingRecipe;
    }

    @Override
    public Item auram$craftingOutputKey() {
        return getCraftingRecipeOutput();
    }
}
