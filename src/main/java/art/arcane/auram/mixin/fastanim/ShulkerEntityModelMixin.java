// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShulkerModel.class, priority = 900)
public class ShulkerEntityModelMixin<T extends Shulker> {
    @Final
    @Shadow
    private ModelPart lid;
    @Final
    @Shadow
    private ModelPart head;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/monster/Shulker;FFFFF)V", cancellable = true)
    public void auram$setAngles(T shulkerEntity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = h - shulkerEntity.tickCount;
        float l = (0.5F + shulkerEntity.getClientPeekAmount(k)) * (float) Math.PI;
        float m = -1.0F + (float)Math.sin(l);
        float n = 0.0F;
        if (l > (float) Math.PI) {
            n = (float)Math.sin(h * 0.1F) * 0.7F;
        }

        this.lid.setPos(0.0F, 16.0F + (float)Math.sin(l) * 8.0F + n, 0.0F);
        this.lid.yRot = shulkerEntity.getClientPeekAmount(k) > 0.3F ? m * m * m * m * (float) Math.PI * 0.125F : 0.0F;
        this.head.xRot = j * (float) (Math.PI / 180.0);
        this.head.yRot = (shulkerEntity.yHeadRot - 180.0F - shulkerEntity.yBodyRot) * (float) (Math.PI / 180.0);
    }
}
