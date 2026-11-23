// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.AllayModel;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.allay.Allay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AllayModel.class)
public abstract class AllayEntityModelMixin extends HierarchicalModel<Allay> implements ArmedModel {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart right_arm;
    @Final
    @Shadow
    private ModelPart left_arm;
    @Final
    @Shadow
    private ModelPart right_wing;
    @Final
    @Shadow
    private ModelPart left_wing;

    @Inject(at = @At("HEAD"), method = "createBodyLayer()Lnet/minecraft/client/model/geom/builders/LayerDefinition;", cancellable = true)
    private static void auram$getTexturedModelData(CallbackInfoReturnable<LayerDefinition> info) {
        if (!FastAnimations.enabled()) return;
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        PartDefinition modelPartData2 = modelPartData.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 23.5F, 0.0F));
        modelPartData2.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -3.99F, 0.0F)
        );
        PartDefinition modelPartData3 = modelPartData2.addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 10)
                .addBox(-1.5F, 0.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(0, 16)
                .addBox(-1.5F, 0.0F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(-0.2F)),
            PartPose.offset(0.0F, -4.0F, 0.0F)
        );
        modelPartData3.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create().texOffs(23, 0).addBox(-0.75F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)),
            PartPose.offset(-1.75F, 0.5F, 0.0F)
        );
        modelPartData3.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create().texOffs(23, 6).addBox(-0.25F, -0.5F, -1.0F, 1.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)),
            PartPose.offset(1.75F, 0.5F, 0.0F)
        );
        modelPartData3.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-0.5F, 0.0F, 0.65F, 0.43633232F, 0.0F, 0.0F)
        );
        modelPartData3.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().texOffs(16, 14).addBox(0.0F, 1.0F, 0.0F, 0.0F, 5.0F, 8.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.5F, 0.0F, 0.65F, 0.43633232F, 0.0F, 0.0F)
        );
        info.setReturnValue(LayerDefinition.create(modelData, 32, 32));
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/animal/allay/Allay;FFFFF)V", cancellable = true)
    public void auram$setAngles(Allay allayEntity, float limbAngle, float limbDistance, float animationProgress, float headPitch, float headYaw, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float m = animationProgress - allayEntity.tickCount;
        float n = animationProgress * 0.15707962F;
        float o = Math.min(limbDistance * 3.3333F, 1.0F);
        float p = 1.0F - o;
        float q = allayEntity.getHoldingItemAnimationProgress(m);
        if (allayEntity.isDancing()) {
            float cos = (float)Math.sin(animationProgress * 0.13962634F + limbDistance + (float) (Math.PI / 2));
            float danceProgress = allayEntity.getSpinningProgress(m);
            this.root.yRot = allayEntity.isSpinning() ? (float) (Math.PI * 4) * danceProgress : this.root.yRot;
            float danceProg2 = 1.0F - danceProgress;
            this.root.zRot = cos * 0.27925268F * danceProg2;
            this.head.yRot = cos * 0.52359873F * danceProg2;
            this.head.zRot = cos * 0.24434608F * danceProg2;
        } else {
            this.head.xRot = headYaw * (float) (Math.PI / 180.0);
            this.head.yRot = headPitch * (float) (Math.PI / 180.0);
        }

        this.right_wing.xRot = 0.43633232F;
        this.left_wing.xRot = 0.43633232F;
        float wingYaws = -0.61086524F + (float)Math.sin(animationProgress * (float) (Math.PI / 9) + limbDistance + (float) (Math.PI / 2)) * 0.4712389F;
        this.right_wing.yRot = wingYaws;
        this.left_wing.yRot = -wingYaws;
        float r = o * (float) (Math.PI * 2.0 / 9.0);
        this.body.xRot = r;
        this.root.y = 23.5F + (float)Math.sin(n + (float) (Math.PI / 2)) * 0.25F * p;
        float pitches = Mth.lerp(q, r, Mth.lerp(o, (float) (-Math.PI / 3), (float) (-Math.PI / 4)));
        this.right_arm.xRot = pitches;
        this.left_arm.xRot = pitches;
        float rolls = 0.43633232F - (float)Math.sin(n + (float) (Math.PI * 3.0 / 2.0) + (float) (Math.PI / 2)) * 0.23561946F * p * (1.0F - q);
        this.right_arm.zRot = rolls;
        this.left_arm.zRot = -rolls;
        float yaws = 0.27925268F * q;
        this.right_arm.yRot = yaws;
        this.left_arm.yRot = -yaws;
    }
}
