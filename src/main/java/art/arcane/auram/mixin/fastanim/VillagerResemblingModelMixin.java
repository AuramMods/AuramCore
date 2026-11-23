// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VillagerModel.class, priority = 900)
public class VillagerResemblingModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart hat;
    @Final
    @Shadow
    private ModelPart hatRim;
    @Final
    @Shadow
    private ModelPart rightLeg;
    @Final
    @Shadow
    private ModelPart leftLeg;
    @Final
    @Shadow
    public ModelPart nose;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        boolean bl = false;
        if (entity instanceof AbstractVillager merchant) {
            bl = merchant.getUnhappyCounter() > 0;
        }

        this.head.yRot = i * (float) (Math.PI / 180.0);
        if (bl) {
            this.head.zRot = 0.3F * (float)Math.sin(0.45F * h);
            this.head.xRot = 0.4F;
        } else {
            this.head.zRot = 0.0F;
            this.head.xRot = j * (float) (Math.PI / 180.0);
        }

        float fastAngle = f * 0.6662F;
        float one4FG = 0.7F * g;
        this.rightLeg.xRot = (float)Math.sin(fastAngle + (float) (Math.PI / 2)) * one4FG;
        this.leftLeg.xRot = (float)Math.sin(fastAngle + (float) Math.PI + (float) (Math.PI / 2)) * one4FG;
    }
}
