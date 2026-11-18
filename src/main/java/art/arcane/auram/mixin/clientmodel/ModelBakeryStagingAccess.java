package art.arcane.auram.mixin.clientmodel;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelBakery.class)
public interface ModelBakeryStagingAccess {
    @Accessor("topLevelModels")
    @Mutable
    void auram$setTopLevelModels(Map<ResourceLocation, UnbakedModel> models);
}
