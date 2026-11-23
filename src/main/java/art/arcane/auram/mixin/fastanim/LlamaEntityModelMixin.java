// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LlamaModel.class)
public class LlamaEntityModelMixin<T extends AbstractChestedHorse> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart body;
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
    private ModelPart rightChest;
    @Final
    @Shadow
    private ModelPart leftChest;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/horse/AbstractChestedHorse;FFFFF)V", cancellable = true)
    public void auram$setAngles(T abstractDonkeyEntity, float limbAngle, float limbDistance, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = i * (float) (Math.PI / 180.0);
        float fastAngle = limbAngle * 0.6662F;
        float fastDistance = 1.4F * limbDistance;
        float angleOne = (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * fastDistance;
        float angleTwo = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * fastDistance;
        this.rightHindLeg.xRot = angleOne;
        this.leftHindLeg.xRot = angleTwo;
        this.rightFrontLeg.xRot = angleTwo;
        this.leftFrontLeg.xRot = angleOne;
        boolean bl = !abstractDonkeyEntity.isBaby() && abstractDonkeyEntity.hasChest();
        this.rightChest.visible = bl;
        this.leftChest.visible = bl;
    }
}
