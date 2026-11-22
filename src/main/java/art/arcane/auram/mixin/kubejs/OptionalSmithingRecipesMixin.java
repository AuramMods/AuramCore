package art.arcane.auram.mixin.kubejs;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashMap;
import java.util.Map;

@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipesEventJS", remap = false)
public abstract class OptionalSmithingRecipesMixin {
    @ModifyVariable(method = "post", at = @At("HEAD"), argsOnly = true)
    private Map<ResourceLocation, JsonElement> auram$filterAbsentSmithingOutputs(Map<ResourceLocation, JsonElement> recipes) {
        Map<ResourceLocation, JsonElement> available = null;
        for (var recipe : recipes.entrySet()) {
            if (!recipe.getKey().getNamespace().equals("upgradabletools") || !recipe.getValue().isJsonObject()) continue;
            var definition = recipe.getValue().getAsJsonObject();
            var type = definition.get("type");
            var result = definition.get("result");
            if (type == null || !type.isJsonPrimitive() || !type.getAsString().equals("minecraft:smithing_transform")
                    || result == null || !result.isJsonObject()) continue;
            var item = result.getAsJsonObject().get("item");
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) continue;
            var output = ResourceLocation.tryParse(item.getAsString());
            if (output == null || BuiltInRegistries.ITEM.containsKey(output)) continue;
            // Upgradable Tools ships optional integrations without conditions for the output mod.
            if (available == null) available = new LinkedHashMap<>(recipes);
            available.remove(recipe.getKey());
        }
        return available == null ? recipes : available;
    }
}
