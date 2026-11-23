// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.GuardianModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuardianModel.class)
public abstract class GuardianEntityModelMixin extends HierarchicalModel<Guardian> {
    @Final
    @Shadow
    private ModelPart root;
    @Final
    @Shadow
    private ModelPart head;
    @Final
    @Shadow
    private ModelPart eye;
    @Final
    @Shadow
    private ModelPart[] tailParts;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Guardian;FFFFF)V", cancellable = true)
    public void auram$setAngles(Guardian guardianEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = h - guardianEntity.tickCount;
        this.head.yRot = i * (float) (Math.PI / 180.0);
        this.head.xRot = j * (float) (Math.PI / 180.0);
        float l = (1.0F - guardianEntity.getSpikesAnimation(k)) * 0.55F;
        this.setupSpikes(h, l);
        Entity entity = Minecraft.getInstance().getCameraEntity();
        if (guardianEntity.hasActiveAttackTarget()) {
            entity = guardianEntity.getActiveAttackTarget();
        }

        if (entity != null) {
            Vec3 vec3d = entity.getEyePosition(0.0F);
            Vec3 vec3d2 = guardianEntity.getEyePosition(0.0F);
            double d = vec3d.y - vec3d2.y;
            this.eye.y = d > 0.0 ? 0.0F : 1.0F;
            Vec3 vec3d3 = guardianEntity.getViewVector(0.0F);
            vec3d3 = new Vec3(vec3d3.x, 0.0, vec3d3.z);
            Vec3 vec3d4 = new Vec3(vec3d2.x - vec3d.x, 0.0, vec3d2.z - vec3d.z).normalize().yRot((float) (Math.PI / 2));
            double e = vec3d3.dot(vec3d4);
            this.eye.x = (float)(Math.sqrt(Math.abs(e)) * 2.0 * Math.signum(e));
        }

        this.eye.visible = true;
        float tailAngleSin = (float)Math.sin(guardianEntity.getTailAnimation(k));
        this.tailParts[0].yRot = tailAngleSin * (float) (Math.PI / 20);
        this.tailParts[1].yRot = tailAngleSin * (float) (Math.PI / 10);
        this.tailParts[2].yRot = tailAngleSin * 0.4712389F;
    }

    @Shadow
    private void setupSpikes(float f, float g) {
    }
}
