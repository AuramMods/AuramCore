// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.BatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BatModel.class)
public class BatEntityModelMixin {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart rightWing;
    @Final
    @Shadow
    private ModelPart leftWing;
    @Final
    @Shadow
    private ModelPart rightWingTip;
    @Final
    @Shadow
    private ModelPart leftWingTip;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/ambient/Bat;FFFFF)V", cancellable = true)
    public void auram$setAngles(Bat batEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        if (batEntity.isResting()) {
            this.head.yRot = (float) Math.PI - i * (float) (Math.PI / 180.0);
            this.head.zRot = (float) Math.PI;
            this.head.setPos(0.0F, -2.0F, 0.0F);
            this.rightWing.setPos(-3.0F, 0.0F, 3.0F);
            this.leftWing.setPos(3.0F, 0.0F, 3.0F);
            this.body.xRot = (float) Math.PI;
            this.rightWing.xRot = (float) (-Math.PI / 20);
            this.rightWing.yRot = (float) (-Math.PI * 2.0 / 5.0);
            this.rightWingTip.yRot = -1.7278761F;
            this.leftWing.xRot = (float) (-Math.PI / 20);
            this.leftWing.yRot = (float) (Math.PI * 2.0 / 5.0);
            this.leftWingTip.yRot = 1.7278761F;
        } else {
            this.head.yRot = i * (float) (Math.PI / 180.0);
            this.head.zRot = 0.0F;
            this.head.setPos(0.0F, 0.0F, 0.0F);
            this.rightWing.setPos(0.0F, 0.0F, 0.0F);
            this.leftWing.setPos(0.0F, 0.0F, 0.0F);
            this.body.xRot = (float) (Math.PI / 4) + (float)Math.cos(h * 0.1F + (float) (Math.PI / 2)) * 0.15F;
            this.body.yRot = 0.0F;
            float yaw = (float)Math.sin(h * 1.3F + (float) (Math.PI / 2)) * (float) (Math.PI / 4);
            this.rightWing.yRot = yaw;
            this.leftWing.yRot = -yaw;
            float yawHalf = yaw * 0.5F;
            this.rightWingTip.yRot = yawHalf;
            this.leftWingTip.yRot = -yawHalf;
        }
    }
}
