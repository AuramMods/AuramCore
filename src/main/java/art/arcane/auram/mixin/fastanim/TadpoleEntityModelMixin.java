// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.TadpoleModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.frog.Tadpole;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TadpoleModel.class, priority = 900)
public class TadpoleEntityModelMixin<T extends Tadpole> {
    @Final
    @Shadow
    private ModelPart tail;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/frog/Tadpole;FFFFF)V", cancellable = true)
    public void auram$setAngles(T tadpoleEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        if (tadpoleEntity.isInWater()) {
            this.tail.yRot = -0.25F * Mth.sin(0.3F * h);
        } else {
            this.tail.yRot = -0.375F * Mth.sin(0.3F * h);
        }
    }
}
