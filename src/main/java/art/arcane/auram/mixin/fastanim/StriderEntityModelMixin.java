// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.StriderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StriderModel.class, priority = 900)
public class StriderEntityModelMixin<T extends Strider> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart rightLeg;
    @Final
    @Shadow
    private ModelPart leftLeg;
    @Final
    @Shadow
    private ModelPart body;
    @Final
    @Shadow
    private ModelPart rightBottomBristle;
    @Final
    @Shadow
    private ModelPart rightMiddleBristle;
    @Final
    @Shadow
    private ModelPart rightTopBristle;
    @Final
    @Shadow
    private ModelPart leftTopBristle;
    @Final
    @Shadow
    private ModelPart leftMiddleBristle;
    @Final
    @Shadow
    private ModelPart leftBottomBristle;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Strider;FFFFF)V", cancellable = true)
    public void auram$setAngles(T striderEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        g = Math.min(0.25F, g);
        if (!striderEntity.isVehicle()) {
            this.body.xRot = j * (float) (Math.PI / 180.0);
            this.body.yRot = i * (float) (Math.PI / 180.0);
        } else {
            this.body.xRot = 0.0F;
            this.body.yRot = 0.0F;
        }

        float fastAngleOne = f * 1.5F;
        float fastAngleTwo = fastAngleOne * 0.5F;
        float fastAngleThree = fastAngleTwo + (float) Math.PI;
        float g2 = 2.0F * g;
        this.body.zRot = 0.1F * (float)Math.sin(fastAngleOne) * 4.0F * g;
        this.body.y = 2.0F;
        this.body.y = this.body.y - 2.0F * (float)Math.sin(fastAngleOne + (float) (Math.PI / 2)) * g2;
        float sinTwo = (float)Math.sin(fastAngleTwo) * g2;
        this.rightLeg.y = 8.0F + 2.0F * sinTwo;
        this.leftLeg.zRot = (float) (Math.PI / 18) * (float)Math.sin(fastAngleTwo + (float) (Math.PI / 2)) * g;
        this.leftLeg.xRot = sinTwo;
        float sinThree = (float)Math.sin(fastAngleThree) * g2;
        this.rightLeg.xRot = sinThree;
        this.rightLeg.zRot = (float) (Math.PI / 18) * (float)Math.sin(fastAngleThree + (float) (Math.PI / 2)) * g;
        this.leftLeg.y = 8.0F + 2.0F * sinThree;
        this.rightBottomBristle.zRot = -1.2217305F;
        this.rightMiddleBristle.zRot = -1.134464F;
        this.rightTopBristle.zRot = -0.87266463F;
        this.leftTopBristle.zRot = 0.87266463F;
        this.leftMiddleBristle.zRot = 1.134464F;
        this.leftBottomBristle.zRot = 1.2217305F;
        float l = (float)Math.cos(f * 1.5F + (float) Math.PI) * g;
        float l3 = l * 1.3F;
        float l2 = l * 1.2F;
        float l6 = l * 0.6F;
        this.rightBottomBristle.zRot += l3;
        this.rightMiddleBristle.zRot += l2;
        this.rightTopBristle.zRot += l6;
        this.leftTopBristle.zRot += l6;
        this.leftMiddleBristle.zRot += l2;
        this.leftBottomBristle.zRot += l3;
        float sinNeg4 = 0.05F * (float)Math.sin(h * -0.4F);
        float sin4 = 0.1F * (float)Math.sin(h * 0.4F);
        float sin2 = 0.1F * (float)Math.sin(h * 0.2F);
        this.rightBottomBristle.zRot += sinNeg4;
        this.rightMiddleBristle.zRot += sin2;
        this.rightTopBristle.zRot += sin4;
        this.leftTopBristle.zRot += sin4;
        this.leftMiddleBristle.zRot += sin2;
        this.leftBottomBristle.zRot += sinNeg4;
    }
}
