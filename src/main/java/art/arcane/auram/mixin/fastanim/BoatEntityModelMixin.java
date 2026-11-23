// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BoatModel.class)
public class BoatEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart leftPaddle;
    @Final
    @Shadow
    private ModelPart rightPaddle;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/vehicle/Boat;FFFFF)V", cancellable = true)
    public void auram$setAngles(Boat boatEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float interpolatePaddlePhase = boatEntity.getRowingTime(0, f);
        this.leftPaddle.xRot = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), ((float)Math.sin(-interpolatePaddlePhase) + 1.0F) * 0.5F);
        this.leftPaddle.yRot = Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), ((float)Math.sin(-interpolatePaddlePhase + 1.0F) + 1.0F) * 0.5F);
        interpolatePaddlePhase = boatEntity.getRowingTime(1, f);
        this.rightPaddle.xRot = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), ((float)Math.sin(-interpolatePaddlePhase) + 1.0F) * 0.5F);
        this.rightPaddle.yRot = (float) Math.PI
            - Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), ((float)Math.sin(-interpolatePaddlePhase + 1.0F) + 1.0F) * 0.5F);
    }

    @Inject(at = @At("HEAD"), method = "animatePaddle(Lnet/minecraft/world/entity/vehicle/Boat;ILnet/minecraft/client/model/geom/ModelPart;F)V", cancellable = true)
    private static void auram$setPaddleAngle(Boat boatEntity, int i, ModelPart modelPart, float f, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float g = boatEntity.getRowingTime(i, f);
        modelPart.xRot = Mth.clampedLerp((float) (-Math.PI / 3), (float) (-Math.PI / 12), ((float)Math.sin(-g) + 1.0F) / 2.0F);
        modelPart.yRot = Mth.clampedLerp((float) (-Math.PI / 4), (float) (Math.PI / 4), ((float)Math.sin(-g + 1.0F) + 1.0F) / 2.0F);
        if (i == 1) {
            modelPart.yRot = (float) Math.PI - modelPart.yRot;
        }
    }
}
