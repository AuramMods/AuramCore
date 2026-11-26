package art.arcane.auram.mixin.kubejs;

import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.brew.Brew;

@Pseudo
@Mixin(targets = "com.prunoideae.schema.BotaniaSchema$3", remap = false)
abstract class BotaniaBrewRegistryMixin {
    @Redirect(method = {"read", "write"}, at = @At(value = "FIELD",
            target = "Lcom/prunoideae/custom/brew/BotaniaRegistryObjectBuilderTypes;BREW_REGISTRY:Lnet/minecraft/core/Registry;"))
    private Registry<Brew> auram$resolveBrewRegistry() {
        // KubeJS Botania can initialize its schemas before Forge creates the brew registry.
        return BotaniaAPI.INSTANCE.getBrewRegistry();
    }
}
