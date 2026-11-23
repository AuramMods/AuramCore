// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WitherBossModel.class, priority = 900)
public class WitherEntityModelMixin<T extends WitherBoss> {
    @Final
    @Shadow
    private ModelPart centerHead;
    @Final
    @Shadow
    private ModelPart rightHead;
    @Final
    @Shadow
    private ModelPart leftHead;
    @Final
    @Shadow
    private ModelPart ribcage;
    @Final
    @Shadow
    private ModelPart tail;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/boss/wither/WitherBoss;FFFFF)V", cancellable = true)
    public void auram$setAngles(T witherEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = (float)Math.sin(h * 0.1F + (float) (Math.PI / 2));
        float ribPitch = (0.065F + 0.05F * k) * (float) Math.PI;
        this.ribcage.xRot = ribPitch;
        this.tail.setPos(-2.0F, 6.9F + (float)Math.sin(ribPitch + (float) (Math.PI / 2)) * 10.0F, -0.5F + (float)Math.sin(ribPitch) * 10.0F);
        this.tail.xRot = (0.265F + 0.1F * k) * (float) Math.PI;
        this.centerHead.yRot = i * (float) (Math.PI / 180.0);
        this.centerHead.xRot = j * (float) (Math.PI / 180.0);
        float bodyYaw = witherEntity.yBodyRot;
        this.rightHead.yRot = (witherEntity.getHeadYRot(0) - bodyYaw) * (float) (Math.PI / 180.0);
        this.rightHead.xRot = witherEntity.getHeadXRot(0) * (float) (Math.PI / 180.0);
        this.leftHead.yRot = (witherEntity.getHeadYRot(1) - bodyYaw) * (float) (Math.PI / 180.0);
        this.leftHead.xRot = witherEntity.getHeadXRot(1) * (float) (Math.PI / 180.0);
    }

    @Inject(at = @At("HEAD"), method = "prepareMobModel(Lnet/minecraft/world/entity/boss/wither/WitherBoss;FFF)V", cancellable = true)
    public void auram$animateModel(T witherEntity, float f, float g, float h, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
    }

    @Inject(at = @At("HEAD"), method = "setupHeadRotation(Lnet/minecraft/world/entity/boss/wither/WitherBoss;Lnet/minecraft/client/model/geom/ModelPart;I)V", cancellable = true)
    private static <T extends WitherBoss> void auram$rotateHead(T witherEntity, ModelPart modelPart, int i, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        modelPart.yRot = (witherEntity.getHeadYRot(i) - witherEntity.yBodyRot) * (float) (Math.PI / 180.0);
        modelPart.xRot = witherEntity.getHeadXRot(i) * (float) (Math.PI / 180.0);
    }
}
