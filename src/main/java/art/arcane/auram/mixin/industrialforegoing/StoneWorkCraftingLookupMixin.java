package art.arcane.auram.mixin.industrialforegoing;

import art.arcane.auram.compat.industrialforegoing.StoneWorkRecipeLookup;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.utils.CraftingUtils", remap = false)
abstract class StoneWorkCraftingLookupMixin {
    @WrapOperation(method = "findOutput", at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;"
                    + "Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"))
    private static Optional<? extends Recipe<?>> auram$reuseFailedCraftingQuery(
            RecipeManager recipes, RecipeType<?> type, Container grid, Level level,
            Operation<Optional<? extends Recipe<?>>> original) {
        return StoneWorkRecipeLookup.find(recipes, grid, level, () -> original.call(recipes, type, grid, level));
    }
}
