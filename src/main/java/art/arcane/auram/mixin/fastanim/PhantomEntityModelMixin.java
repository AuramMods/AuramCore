// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomModel.class)
public class PhantomEntityModelMixin<T extends Phantom> {
    @Final
    @Shadow
    private ModelPart leftWingBase;
    @Final
    @Shadow
    private ModelPart leftWingTip;
    @Final
    @Shadow
    private ModelPart rightWingBase;
    @Final
    @Shadow
    private ModelPart rightWingTip;
    @Final
    @Shadow
    private ModelPart tailBase;
    @Final
    @Shadow
    private ModelPart tailTip;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Phantom;FFFFF)V", cancellable = true)
    public void auram$setAngles(T phantomEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = (phantomEntity.getUniqueFlapTickOffset() + h) * 7.448451F * (float) (Math.PI / 180.0);
        float rolls = (float)Math.sin(k + (float) (Math.PI / 2)) * 0.27925268F;
        this.leftWingBase.zRot = rolls;
        this.leftWingTip.zRot = rolls;
        this.rightWingBase.zRot = -rolls;
        this.rightWingTip.zRot = -rolls;
        float pitches = -(5.0F + (float)Math.sin(k * 2.0F + (float) (Math.PI / 2)) * 5.0F) * (float) (Math.PI / 180.0);
        this.tailBase.xRot = pitches;
        this.tailTip.xRot = pitches;
    }
}
