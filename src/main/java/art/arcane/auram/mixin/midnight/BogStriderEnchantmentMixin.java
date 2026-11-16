package art.arcane.auram.mixin.midnight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Pseudo
@Mixin(targets = "com.crypticmushroom.minecraft.midnight.common.enchantment.BogStriderEnchantment", remap = false)
public abstract class BogStriderEnchantmentMixin {
    @Shadow(remap = false)
    @Final
    private static UUID SPEED_MODIFIER_UUID;

    @Unique
    private static final ResourceLocation auram$BOG_STRIDER = ResourceLocation.tryBuild("midnight", "bog_strider");

    @Shadow(remap = false)
    private static void removeModifier(LivingEntity entity) {
        throw new AssertionError();
    }

    @Inject(
            method = {
                    "onChangedBlock(Lnet/minecraft/world/entity/LivingEntity;)V",
                    "onFall(Lnet/minecraft/world/entity/LivingEntity;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void auram$avoidSynchronousChunkLoads(LivingEntity entity, CallbackInfo ci) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean hasModifier = movementSpeed != null && movementSpeed.getModifier(SPEED_MODIFIER_UUID) != null;
        Enchantment bogStrider = ForgeRegistries.ENCHANTMENTS.getValue(auram$BOG_STRIDER);
        if (!hasModifier && (bogStrider == null || EnchantmentHelper.getEnchantmentLevel(bogStrider, entity) == 0)) {
            ci.cancel();
            return;
        }

        // In 1.20.1 getOnPos() computes the support position without reading terrain.
        // Midnight also checks a block directly below the entity in onBogBlock().
        if (!auram$isChunkLoaded(level, entity.getOnPos()) || !auram$isChunkLoaded(level, entity.blockPosition())) {
            removeModifier(entity);
            ci.cancel();
        }
    }

    @Unique
    private static boolean auram$isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        ) != null;
    }
}
