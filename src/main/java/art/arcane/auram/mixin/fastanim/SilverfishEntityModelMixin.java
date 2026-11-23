// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SilverfishModel.class, priority = 900)
public class SilverfishEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private final ModelPart[] bodyParts = new ModelPart[7];
    @Final
    @Shadow
    private final ModelPart[] bodyLayers = new ModelPart[3];

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();

        for (int k = 0; k < this.bodyParts.length; k++) {
            float abs = Math.abs(k - 2);
            float inside = h * 0.9F + k * 0.4712389F;
            this.bodyParts[k].yRot = (float)Math.sin(inside + (float) (Math.PI / 2)) * (float) (Math.PI / 20) * (1.0F + abs);
            this.bodyParts[k].x = (float)Math.sin(inside) * (float) (Math.PI / 5) * abs;
        }

        this.bodyLayers[0].yRot = this.bodyParts[2].yRot;
        this.bodyLayers[1].yRot = this.bodyParts[4].yRot;
        this.bodyLayers[1].x = this.bodyParts[4].x;
        this.bodyLayers[2].yRot = this.bodyParts[1].yRot;
        this.bodyLayers[2].x = this.bodyParts[1].x;
    }
}
