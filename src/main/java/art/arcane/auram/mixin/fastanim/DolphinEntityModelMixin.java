// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DolphinModel.class)
public class DolphinEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart tail;
    @Final
    @Shadow
    private ModelPart tailFin;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.body.xRot = j * (float) (Math.PI / 180.0);
        this.body.yRot = i * (float) (Math.PI / 180.0);
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7) {
            float cos = (float)Math.sin(h * 0.3F + (float) (Math.PI / 2));
            this.body.xRot += -0.05F - 0.05F * cos;
            this.tail.xRot = -0.1F * cos;
            this.tailFin.xRot = -0.2F * cos;
        }
    }
}
