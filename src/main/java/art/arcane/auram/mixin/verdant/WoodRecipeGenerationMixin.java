package art.arcane.auram.mixin.verdant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.ordana.verdant.dynamicpack.ServerDynamicResourcesHandler", remap = false)
public abstract class WoodRecipeGenerationMixin {
    @Unique private static final ResourceLocation AURAM_MISSING_WOOD = ResourceLocation.parse("auram:missing_verdant_wood");
    @Unique private static final String AURAM_MISSING_WOOD_ID = AURAM_MISSING_WOOD.toString();

    @WrapOperation(method = "regenerateDynamicAssets", at = @At(value = "INVOKE", target =
            "Lnet/mehvahdjukaar/moonlight/api/util/Utils;getID(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation auram$markMissingWoodVariant(Object variant, Operation<ResourceLocation> original) {
        return variant == null ? AURAM_MISSING_WOOD : original.call(variant);
    }

    @Inject(method = "unstripLogBuilder", at = @At("HEAD"), cancellable = true)
    private void auram$skipRecipeForMissingVariant(@Coerce Object template, String templateId, String templateInput,
            String templateOutput, String recipeId, String barkId, String input, String output, CallbackInfo callback) {
        // A wood set can have logs without wood or stripped forms. Preserve every recipe with both variants.
        if (AURAM_MISSING_WOOD_ID.equals(input) || AURAM_MISSING_WOOD_ID.equals(output)) callback.cancel();
    }
}
