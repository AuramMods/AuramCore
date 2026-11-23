// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer.DragonModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DragonModel.class)
public class DragonEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart neck;
    @Final
    @Shadow
    private ModelPart jaw;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart leftWing;
    @Final
    @Shadow
    private ModelPart leftWingTip;
    @Final
    @Shadow
    private ModelPart leftFrontLeg;
    @Final
    @Shadow
    private ModelPart leftFrontLegTip;
    @Final
    @Shadow
    private ModelPart leftFrontFoot;
    @Final
    @Shadow
    private ModelPart leftRearLeg;
    @Final
    @Shadow
    private ModelPart leftRearLegTip;
    @Final
    @Shadow
    private ModelPart leftRearFoot;
    @Final
    @Shadow
    private ModelPart rightWing;
    @Final
    @Shadow
    private ModelPart rightWingTip;
    @Final
    @Shadow
    private ModelPart rightFrontLeg;
    @Final
    @Shadow
    private ModelPart rightFrontLegTip;
    @Final
    @Shadow
    private ModelPart rightFrontFoot;
    @Final
    @Shadow
    private ModelPart rightRearLeg;
    @Final
    @Shadow
    private ModelPart rightRearLegTip;
    @Final
    @Shadow
    private ModelPart rightRearFoot;
    @Shadow
    @Nullable
    private EnderDragon entity;
    @Shadow
    private float a;

    @Inject(at = @At("HEAD"), method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", cancellable = true)
    public void auram$render(PoseStack matrixStack, VertexConsumer vertexConsumer, int i, int j, float f, float g, float h, float k, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        matrixStack.pushPose();
        float l = Mth.lerp(this.a, this.entity.oFlapTime, this.entity.flapTime);
        float lPi2 = l * (float) (Math.PI * 2);
        this.jaw.xRot = (float)(Math.sin(lPi2) + 1.0) * 0.2F;
        float m = (float)(Math.sin(lPi2 - 1.0F) + 1.0);
        m = (m * m + m * 2.0F) * 0.05F;
        matrixStack.translate(0.0, m - 2.0F, -3.0);
        matrixStack.mulPose(Axis.XP.rotationDegrees(m * 2.0F));
        float n = 0.0F;
        float o = 20.0F;
        float p = -12.0F;
        double[] ds = this.entity.getLatencyPos(6, this.a);
        float r = (float)Mth.wrapDegrees(this.entity.getLatencyPos(5, this.a)[0] - this.entity.getLatencyPos(10, this.a)[0]);
        float s = (float)Mth.wrapDegrees(this.entity.getLatencyPos(5, this.a)[0] + r / 2.0F);

        for (int u = 0; u < 5; u++) {
            double[] es = this.entity.getLatencyPos(5 - u, this.a);
            float v = (float)Math.sin(u * 0.45F + lPi2 + (float) (Math.PI / 2)) * 0.15F;
            this.neck.yRot = (float)(Mth.wrapDegrees(es[0] - ds[0]) * 0.026179938F);
            this.neck.xRot = v + this.entity.getHeadPartYOffset(u, ds, es) * 0.13089968F;
            this.neck.zRot = (float)(-Mth.wrapDegrees(es[0] - s) * 0.13089968F);
            this.neck.y = o;
            this.neck.z = p;
            this.neck.x = n;
            o = (float)(o + Math.sin(this.neck.xRot) * 10.0);
            float cosNeckPitch = (float)Math.cos(this.neck.xRot) * 10.0F;
            p = (float)(p - Math.sin(this.neck.yRot + (float) (Math.PI / 2)) * cosNeckPitch);
            n = (float)(n - Math.sin(this.neck.yRot) * cosNeckPitch);
            this.neck.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, k);
        }

        this.head.y = o;
        this.head.z = p;
        this.head.x = n;
        double[] fs = this.entity.getLatencyPos(0, this.a);
        this.head.yRot = (float)(Mth.wrapDegrees(fs[0] - ds[0]) * (float) (Math.PI / 180.0));
        this.head.xRot = Mth.wrapDegrees(this.entity.getHeadPartYOffset(6, ds, fs)) * 0.13089968F;
        this.head.zRot = (float)(-Mth.wrapDegrees(fs[0] - s) * (float) (Math.PI / 180.0));
        this.head.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, k);
        matrixStack.pushPose();
        matrixStack.translate(0.0, 1.0, 0.0);
        matrixStack.mulPose(Axis.ZP.rotationDegrees(-r * 1.5F));
        matrixStack.translate(0.0, -1.0, 0.0);
        this.body.zRot = 0.0F;
        this.body.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, k);
        this.leftWing.xRot = 0.125F - (float)Math.cos(lPi2) * 0.2F;
        this.leftWing.yRot = -0.25F;
        this.leftWing.zRot = -((float)(Math.sin(lPi2) + 0.125)) * 0.8F;
        this.leftWingTip.zRot = (float)(Math.sin(lPi2 + 2.0F) + 0.5) * 0.75F;
        this.rightWing.xRot = this.leftWing.xRot;
        this.rightWing.yRot = -this.leftWing.yRot;
        this.rightWing.zRot = -this.leftWing.zRot;
        this.rightWingTip.zRot = -this.leftWingTip.zRot;
        this.renderSide(
            matrixStack,
            vertexConsumer,
            i,
            j,
            m,
            this.leftWing,
            this.leftFrontLeg,
            this.leftFrontLegTip,
            this.leftFrontFoot,
            this.leftRearLeg,
            this.leftRearLegTip,
            this.leftRearFoot,
            k
        );
        this.renderSide(
            matrixStack,
            vertexConsumer,
            i,
            j,
            m,
            this.rightWing,
            this.rightFrontLeg,
            this.rightFrontLegTip,
            this.rightFrontFoot,
            this.rightRearLeg,
            this.rightRearLegTip,
            this.rightRearFoot,
            k
        );
        matrixStack.popPose();
        o = 10.0F;
        p = 60.0F;
        n = 0.0F;
        ds = this.entity.getLatencyPos(11, this.a);

        for (int x = 0; x < 12; x++) {
            fs = this.entity.getLatencyPos(12 + x, this.a);
            this.neck.yRot = (float)((Mth.wrapDegrees(fs[0] - ds[0]) * 1.5 + 180.0) * (float) (Math.PI / 180.0));
            this.neck.xRot = (float)(Math.sin(x * 0.45F + lPi2) * 0.05F + (float)(fs[1] - ds[1]) * 0.13089968F);
            this.neck.zRot = (float)(Mth.wrapDegrees(fs[0] - s) * (float) (Math.PI / 180.0) * 1.5);
            this.neck.y = o;
            this.neck.z = p;
            this.neck.x = n;
            o += (float)Math.sin(this.neck.xRot) * 10.0F;
            float cosNeckPitch = (float)(Math.sin(this.neck.xRot + (float) (Math.PI / 2)) * 10.0);
            p -= (float)Math.sin(this.neck.yRot + (float) (Math.PI / 2)) * cosNeckPitch;
            n -= (float)Math.sin(this.neck.yRot) * cosNeckPitch;
            this.neck.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, k);
        }

        matrixStack.popPose();
    }

    @Shadow
    private void renderSide(
        PoseStack matrixStack,
        VertexConsumer vertexConsumer,
        int i,
        int j,
        float f,
        ModelPart modelPart,
        ModelPart modelPart2,
        ModelPart modelPart3,
        ModelPart modelPart4,
        ModelPart modelPart5,
        ModelPart modelPart6,
        ModelPart modelPart7,
        float g
    ) {
    }

    @Inject(at = @At("HEAD"), method = "renderSide(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFLnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;F)V", cancellable = true)
    private void auram$setLimbRotation(
        PoseStack matrixStack,
        VertexConsumer vertexConsumer,
        int i,
        int j,
        float f,
        ModelPart modelPart,
        ModelPart modelPart2,
        ModelPart modelPart3,
        ModelPart modelPart4,
        ModelPart modelPart5,
        ModelPart modelPart6,
        ModelPart modelPart7,
        float g,
        CallbackInfo info
    ) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float f1 = f * 0.1F;
        modelPart5.xRot = 1.0F + f1;
        modelPart6.xRot = 0.5F + f1;
        modelPart7.xRot = 0.75F + f1;
        modelPart2.xRot = 1.3F + f1;
        modelPart3.xRot = -0.5F - f1;
        modelPart4.xRot = 0.75F + f1;
        modelPart.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, g);
        modelPart2.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, g);
        modelPart5.render(matrixStack, vertexConsumer, i, j, 1.0F, 1.0F, 1.0F, g);
    }
}
