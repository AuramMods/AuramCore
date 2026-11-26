package art.arcane.auram.mixin.almostunified;

import art.arcane.auram.compat.almostunified.CraftingRecipeOutput;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.almostreliable.unified.recipe.RecipeTransformer", remap = false)
abstract class CraftingDuplicateGroupsMixin {
    @WrapOperation(method = "handleDuplicates", at = @At(value = "INVOKE", target =
            "Lcom/almostreliable/unified/recipe/RecipeTransformer;handleDuplicate("
                    + "Lcom/almostreliable/unified/recipe/RecipeLink;Ljava/util/List;)Z"))
    private boolean auram$compareRecipesWithMatchingOutputs(@Coerce Object transformer, @Coerce Object link,
                                                             List<?> candidates, Operation<Boolean> original,
                                                             @Share("craftingOutputs") LocalRef<Map<Item, List<Object>>> groups) {
        var recipe = (CraftingRecipeOutput) link;
        if (!recipe.auram$hasCraftingOutputKey()) {
            return original.call(transformer, link, candidates);
        }

        Map<Item, List<Object>> byOutput = groups.get();
        if (byOutput == null) {
            byOutput = new IdentityHashMap<>();
            for (Object candidate : candidates) {
                var output = (CraftingRecipeOutput) candidate;
                if (!output.auram$hasCraftingOutputKey()) {
                    return original.call(transformer, link, candidates);
                }
                byOutput.computeIfAbsent(output.auram$craftingOutputKey(), key -> new ArrayList<>()).add(candidate);
            }
            groups.set(byOutput);
        }
        // Their existing predicate rejects different output items before comparing ingredients and rules.
        return original.call(transformer, link, byOutput.getOrDefault(recipe.auram$craftingOutputKey(), List.of()));
    }
}
