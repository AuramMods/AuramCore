package art.arcane.auram.mixin.felling;

import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.auram.compat.adapt.FelledTreeXp;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = SkillAxes.class, remap = false)
public abstract class AdaptAxesCuttingToolMixin {
    @ModifyArg(method = "on(Lart/arcane/adapt/platform/event/block/BlockBreakEvent;)V",
            at = @At(value = "INVOKE", target = "Lart/arcane/adapt/api/item/Tools;serves("
                    + "Lart/arcane/adapt/platform/item/ItemRef;Lart/arcane/adapt/platform/world/BlockStateRef;"
                    + "Lart/arcane/adapt/platform/item/ToolKind;)Z"), index = 0, require = 1)
    private ItemRef auram$useCuttingTool(ItemRef held, @Local(argsOnly = true) BlockBreakEvent event) {
        return event instanceof FelledTreeXp.Break felled ? felled.tool() : held;
    }
}
