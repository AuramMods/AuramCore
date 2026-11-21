package art.arcane.auram.mixin.immersiveweathering;

import art.arcane.auram.util.LeafPileContact;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LeafPileSpeedMixin implements LeafPileContact {
    @Unique private int auram$lastLeafPileContact = -2;

    @Override
    public void auram$markLeafPileContact() {
        auram$lastLeafPileContact = ((LivingEntity) (Object) this).tickCount;
    }

    @ModifyReturnValue(method = "getAttributeValue(Lnet/minecraft/world/entity/ai/attributes/Attribute;)D",
            at = @At("RETURN"))
    private double auram$scaleLeafPileSpeed(double speed, Attribute attribute) {
        if (attribute != Attributes.MOVEMENT_SPEED) return speed;
        int ticks = ((LivingEntity) (Object) this).tickCount - auram$lastLeafPileContact;
        // Block contact is reported during movement, after that tick's speed was read.
        return ticks == 0 || ticks == 1 ? speed * 0.9D : speed;
    }
}
