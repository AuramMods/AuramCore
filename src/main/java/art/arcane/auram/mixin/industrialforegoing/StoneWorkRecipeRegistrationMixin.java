package art.arcane.auram.mixin.industrialforegoing;

import art.arcane.auram.compat.industrialforegoing.StoneWorkRecipeLookup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.buuz135.industrial.plugin.jei.JEICustomPlugin", remap = false)
abstract class StoneWorkRecipeRegistrationMixin {
    @WrapMethod(method = "registerRecipes")
    private void auram$shareStoneWorkMisses(IRecipeRegistration registration, Operation<Void> original) {
        StoneWorkRecipeLookup.duringRegistration(() -> original.call(registration));
    }
}
