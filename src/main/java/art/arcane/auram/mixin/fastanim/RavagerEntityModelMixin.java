// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.RavagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RavagerModel.class, priority = 900)
public class RavagerEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart mouth;
    @Final
    @Shadow
    private ModelPart rightHindLeg;
    @Final
    @Shadow
    private ModelPart leftHindLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart neck;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Ravager;FFFFF)V", cancellable = true)
    public void auram$setAngles(Ravager ravagerEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float fastAngle = f * 0.6662F;
        float k = 0.4F * g;
        float firstAngle = (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * k;
        float secondAngle = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * k;
        this.rightHindLeg.xRot = firstAngle;
        this.leftHindLeg.xRot = secondAngle;
        this.rightFrontLeg.xRot = secondAngle;
        this.leftFrontLeg.xRot = firstAngle;
    }

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/monster/Ravager;FFF)V", cancellable = true)
    public void auram$animateModel(Ravager ravagerEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        int stunTick = ravagerEntity.getStunnedTick();
        int roarTick = ravagerEntity.getRoarTick();
        int attackTick = ravagerEntity.getAttackTick();
        if (attackTick > 0) {
            float n = Mth.triangleWave(attackTick - h, 10.0F);
            float o = (1.0F + n) * 0.5F;
            float p = o * o * o * 12.0F;
            float q = p * (float)Math.sin(this.neck.xRot);
            this.neck.z = -6.5F + p;
            this.neck.y = -7.0F - q;
            this.mouth.xRot = attackTick > 5
                ? (float)Math.sin((-4 + attackTick - h) / 4.0F) * (float) (Math.PI * 2.0 / 5.0)
                : (float) (Math.PI / 20) * (float)Math.sin((float) Math.PI * (attackTick - h) * 0.01F);
        } else {
            float o = -1.0F * (float)Math.sin(this.neck.xRot);
            this.neck.x = 0.0F;
            this.neck.y = -7.0F - o;
            this.neck.z = 5.5F;
            boolean bl = stunTick > 0;
            this.neck.xRot = bl ? 0.21991149F : 0.0F;
            this.mouth.xRot = (float) Math.PI * (bl ? 0.05F : 0.01F);
            if (bl) {
                double d = stunTick / 40.0;
                this.neck.x = (float)Math.sin(d * 10.0) * 3.0F;
            } else if (roarTick > 0) {
                float q = (float)Math.sin((20 - roarTick - h) * 0.03926991F);
                this.mouth.xRot = (float) (Math.PI / 2) * q;
            }
        }
    }
}
