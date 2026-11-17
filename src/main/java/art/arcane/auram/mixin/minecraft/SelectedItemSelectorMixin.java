package art.arcane.auram.mixin.minecraft;

import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Project only the field that vanilla overwrites after entity serialization. */
@Mixin(EntitySelectorOptions.class)
public abstract class SelectedItemSelectorMixin {
    @Inject(method = "lambda$bootStrap$49(Lnet/minecraft/nbt/CompoundTag;ZLnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void auram$matchSelectedItem(CompoundTag expected, boolean inverted, Entity entity,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ServerPlayer player)) return;
        Tag selected = expected.get("SelectedItem");
        if (selected == null) return;
        ItemStack held = player.getInventory().getSelected();
        // Empty hands do not overwrite SelectedItem in vanilla. Preserve any
        // custom entity serializer's field in that case by taking the fallback.
        if (held.isEmpty()) return;
        boolean matches = NbtUtils.compareNbt(selected, held.save(new CompoundTag()), true);
        if (!matches || expected.size() == 1) cir.setReturnValue(matches != inverted);
        // A successful partial check with other root keys still needs the full
        // serializer. Never cache entity/stack NBT across predicates or commands.
    }
}
