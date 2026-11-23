// Port of FastAnim 1.6 by Lunade Music. See META-INF/licenses/fastanim.txt.
package art.arcane.auram.mixin.fastanim;

import art.arcane.auram.tweak.FastAnimations;
import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlazeModel.class)
public class BlazeEntityModelMixin<T extends Entity> {
    @Final
    @Shadow
    private ModelPart[] upperBodyParts;
    @Final
    @Shadow
    private ModelPart head;

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", cancellable = true)
    public void auram$setAngles(T entity, float f, float g, float h, float i, float j, CallbackInfo info) {
        if (!FastAnimations.enabled()) return;
        info.cancel();
        float k = h * (float) (-Math.PI / 10);

        for (int l = 0; l < 4; l++) {
            this.upperBodyParts[l].y = -2.0F + (float)Math.sin((l * 2 + h) * 0.25F + (float) (Math.PI / 2));
            this.upperBodyParts[l].x = (float)Math.sin(k + (float) (Math.PI / 2)) * 9.0F;
            this.upperBodyParts[l].z = (float)Math.sin(k) * 9.0F;
            k += (float) (Math.PI / 2);
        }

        k = (float) (Math.PI / 4) + h * 0.09424778F;

        for (int var10 = 4; var10 < 8; var10++) {
            this.upperBodyParts[var10].y = 2.0F + (float)Math.sin((var10 * 2 + h) * 0.25F + (float) (Math.PI / 2));
            this.upperBodyParts[var10].x = (float)Math.sin(k + (float) (Math.PI / 2)) * 7.0F;
            this.upperBodyParts[var10].z = (float)Math.sin(k) * 7.0F;
            k += (float) (Math.PI / 2);
        }

        k = 0.47123894F + h * (float) (-Math.PI / 20);

        for (int var11 = 8; var11 < 12; var11++) {
            this.upperBodyParts[var11].y = 11.0F + (float)Math.sin((var11 * 1.5F + h) * 0.5F + (float) (Math.PI / 2));
            this.upperBodyParts[var11].x = (float)Math.sin(k + (float) (Math.PI / 2)) * 5.0F;
            this.upperBodyParts[var11].z = (float)Math.sin(k) * 5.0F;
            k += (float) (Math.PI / 2);
        }

        this.head.yRot = i * (float) (Math.PI / 180.0);
        this.head.xRot = j * (float) (Math.PI / 180.0);
    }
}
