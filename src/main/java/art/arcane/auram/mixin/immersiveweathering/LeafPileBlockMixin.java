package art.arcane.auram.mixin.immersiveweathering;

import art.arcane.auram.util.LeafPileContact;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.blocks.LeafPileBlock", remap = false)
public abstract class LeafPileBlockMixin {
    @Redirect(method = {"entityInside", "m_7892_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V",
            remap = true), remap = false)
    private void auram$slowWithoutSticking(Entity entity, BlockState state, Vec3 slowdown) {
        // LeafPileBlock has already checked pile depth, config and entity exemptions.
        ((LeafPileContact) entity).auram$markLeafPileContact();
    }
}
