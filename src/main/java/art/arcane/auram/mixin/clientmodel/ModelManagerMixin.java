package art.arcane.auram.mixin.clientmodel;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(ModelManager.class)
public abstract class ModelManagerMixin {
    @Shadow(remap = false)
    public abstract ModelBakery getModelBakery();

    @Inject(method = "apply", at = @At("RETURN"))
    private void auram$releaseModelStaging(CallbackInfo ci) {
        ModelBakery bakery = getModelBakery();
        for (Class<?> type : bakery.getClass().getInterfaces()) {
            if (type.getName().equals("org.embeddedt.modernfix.duck.IExtendedModelBakery")) return;
        }

        // The top-level selection is used during reload to resolve parents and
        // bake the registry. Forge's model events and model cache have finished.
        // Retain the bakery and its model sources for renderers that bake later.
        ((ModelBakeryStagingAccess) bakery).auram$setTopLevelModels(new HashMap<>());
    }
}
