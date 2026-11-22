package art.arcane.auram.mixin.mekanism;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;

@Pseudo
@Mixin(targets = "com.jerry.mekmm.client.jei.MoreMachineRecipeRegistryHelper", remap = false)
public abstract class JeiReplicatorRecipeMixin {
    @WrapOperation(method = {"registerItemReplicator", "registerFluidReplicator"}, at = @At(value = "INVOKE", target =
            "Ljava/util/HashMap;containsKey(Ljava/lang/Object;)Z"))
    private static boolean auram$checkAvailableRecipes(HashMap<?, ?> recipes, Object key, Operation<Boolean> original) {
        // A client can reach JEI before the recipe map is available. Let the rest of the plugin register.
        return recipes != null && original.call(recipes, key);
    }
}
