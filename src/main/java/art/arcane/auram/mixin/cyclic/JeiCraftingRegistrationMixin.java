package art.arcane.auram.mixin.cyclic;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "com.lothrazar.cyclic.compat.jei.CyclicPluginJEI", remap = false)
public abstract class JeiCraftingRegistrationMixin {
    @WrapWithCondition(method = "registerRecipes", at = @At(value = "INVOKE", target =
            "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"))
    private boolean auram$skipVanillaCraftingRegistration(IRecipeRegistration registration,
                                                         RecipeType<?> type, List<?> recipes) {
        // JEI's own plugin registers crafting recipes. Cyclic also uses them for its separate Packager category.
        return !RecipeTypes.CRAFTING.equals(type);
    }
}
