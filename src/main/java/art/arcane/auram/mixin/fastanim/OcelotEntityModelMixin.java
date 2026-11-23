// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OcelotModel.class)
public abstract class OcelotEntityModelMixin<T extends Entity> extends AgeableListModel<T> {
    @Final
    @Shadow
    public ModelPart leftHindLeg;
    @Final
    @Shadow
    public ModelPart rightHindLeg;
    @Final
    @Shadow
    public ModelPart leftFrontLeg;
    @Final
    @Shadow
    public ModelPart rightFrontLeg;
    @Final
    @Shadow
    public ModelPart tail1;
    @Final
    @Shadow
    public ModelPart tail2;
    @Final
    @Shadow
    public ModelPart head;
    @Final
    @Shadow
    public ModelPart body;
    @Shadow
    public int state = 1;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        if (this.state != 3) {
            this.body.xRot = (float) (Math.PI / 2);
            float legPitch = (float)(Math.sin(f * 0.6662F + (float) Math.PI + (float) (Math.PI / 2)) * g);
            if (this.state == 2) {
                this.leftHindLeg.xRot = (float)(Math.sin(f * 0.6662F * g) + (float) (Math.PI / 2));
                this.rightHindLeg.xRot = (float)(Math.sin(f * 0.6662F + 0.3F + (float) (Math.PI / 2)) * g);
                this.leftFrontLeg.xRot = (float)(Math.sin(f * 0.6662F + (float) Math.PI + 0.3F + (float) (Math.PI / 2)) * g);
                this.rightFrontLeg.xRot = legPitch;
                this.tail2.xRot = (float)(1.7278761F + (float) (Math.PI / 10) * Math.sin(f + (float) (Math.PI / 2)) * g);
            } else {
                this.leftHindLeg.xRot = (float)(Math.sin(f * 0.6662F + (float) (Math.PI / 2)) * g);
                this.rightHindLeg.xRot = legPitch;
                this.leftFrontLeg.xRot = legPitch;
                this.rightFrontLeg.xRot = (float)(Math.sin(f * 0.6662F + (float) (Math.PI / 2)) * g);
                if (this.state == 1) {
                    this.tail2.xRot = (float)(1.7278761F + (float) (Math.PI / 4) * Math.sin(f + (float) (Math.PI / 2)) * g);
                } else {
                    this.tail2.xRot = (float)(1.7278761F + 0.47123894F * Math.sin(f + (float) (Math.PI / 2)) * g);
                }
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/Entity;FFF)V", cancellable = true)
    public void auram$animateModel(T entity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.body.y = 12.0F;
        this.body.z = -10.0F;
        this.head.y = 15.0F;
        this.head.z = -9.0F;
        this.tail1.y = 15.0F;
        this.tail1.z = 8.0F;
        this.tail2.y = 20.0F;
        this.tail2.z = 14.0F;
        this.tail1.xRot = 0.9F;
        if (entity.isCrouching()) {
            this.body.y = 13.0F;
            this.head.y = 17.0F;
            this.tail1.y = 16.0F;
            this.tail2.y = 16.0F;
            this.tail2.z = 16.0F;
            this.tail1.xRot = (float) (Math.PI / 2);
            this.tail2.xRot = (float) (Math.PI / 2);
            this.state = 0;
        } else if (entity.isSprinting()) {
            this.tail2.y = 20.0F;
            this.tail2.z = 16.0F;
            this.tail1.xRot = (float) (Math.PI / 2);
            this.tail2.xRot = (float) (Math.PI / 2);
            this.state = 2;
        } else {
            this.state = 0;
        }
    }
}
