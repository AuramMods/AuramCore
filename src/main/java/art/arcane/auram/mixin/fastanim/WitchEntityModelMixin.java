// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WitchModel.class, priority = 900)
public abstract class WitchEntityModelMixin<T extends Entity> extends VillagerModel<T> {
    @Shadow
    private boolean holdingItem;

    public WitchEntityModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        super.setupAnim(entity, f, g, h, i, j);
        float k = 0.01F * (entity.getId() % 10);
        float newAge = entity.tickCount * k;
        this.nose.zRot = (float)Math.sin(newAge + (float) (Math.PI / 2)) * 0.04363323F;
        if (this.holdingItem) {
            this.nose.setPos(0.0F, 1.0F, -1.5F);
            this.nose.xRot = -0.9F;
        } else {
            this.nose.xRot = (float)Math.sin(newAge) * 0.07853981F;
            this.nose.setPos(0.0F, -2.0F, 0.0F);
        }
    }
}
