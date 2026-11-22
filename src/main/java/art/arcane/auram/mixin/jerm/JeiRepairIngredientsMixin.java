package art.arcane.auram.mixin.jerm;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "github.iri.jem.jei.ModJeiPlugin", remap = false)
public abstract class JeiRepairIngredientsMixin {
    @WrapOperation(method = "lambda$getRepairData$1", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/item/crafting/Ingredient;getItems()[Lnet/minecraft/world/item/ItemStack;", remap = true))
    private static ItemStack[] auram$skipUnavailableRepairIngredient(Ingredient ingredient, Operation<ItemStack[]> original) {
        return original.call(ingredient == null ? Ingredient.EMPTY : ingredient);
    }
}
