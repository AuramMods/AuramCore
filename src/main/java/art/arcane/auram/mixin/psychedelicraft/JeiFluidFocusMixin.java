package art.arcane.auram.mixin.psychedelicraft;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

@Pseudo
@Mixin(targets = "ivorius.psychedelicraft.compat.jei.FluidCraftingExtension", remap = false)
public abstract class JeiFluidFocusMixin {
    @Redirect(method = "setRecipe", at = @At(value = "INVOKE", target =
            "Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;createFocusLink(Ljava/util/Collection;)V"), require = 0)
    private void auram$linkFluidAlternatives(IRecipeLayoutBuilder builder,
                                             Collection<? extends IIngredientAcceptor<?>> slots) {
        builder.createFocusLink(slots.toArray(IIngredientAcceptor<?>[]::new));
    }
}
