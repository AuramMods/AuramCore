package art.arcane.auram.mixin.minecraft;

import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(EntitySelectorOptions.class)
public abstract class DroppedItemTagSelectorMixin {
    @Inject(method = "lambda$bootStrap$49(Lnet/minecraft/nbt/CompoundTag;ZLnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void auram$matchDroppedStack(CompoundTag expected, boolean inverted, Entity entity,
                                                CallbackInfoReturnable<Boolean> ci) {
        // Custom entities can write different fields in addAdditionalSaveData.
        if (entity.getClass() != ItemEntity.class || !(expected.get("Item") instanceof CompoundTag item)) return;

        Tag fire = expected.get("Fire");
        if (expected.size() != (fire == null ? 1 : 2) || (fire != null && !(fire instanceof ShortTag))) return;
        Tag id = item.get("id");
        Tag count = item.get("Count");
        Tag tag = item.get("tag");
        int fields = (id == null ? 0 : 1) + (count == null ? 0 : 1) + (tag == null ? 0 : 1);
        if (fields != item.size() || (id != null && !(id instanceof StringTag))
                || (count != null && !(count instanceof ByteTag)) || (tag != null && !(tag instanceof CompoundTag))) return;

        ItemStack stack = ((ItemEntity) entity).getItem();
        // ItemEntity emits Item only for a nonempty stack. ItemStack.save writes
        // these three fields directly, and NBT comparison reads them without mutation.
        boolean matches = !stack.isEmpty()
                && (fire == null || ((ShortTag) fire).getAsShort() == (short) entity.getRemainingFireTicks())
                && (id == null || id.getAsString().equals(Objects.toString(BuiltInRegistries.ITEM.getKey(stack.getItem()), "minecraft:air")))
                && (count == null || ((ByteTag) count).getAsByte() == (byte) stack.getCount())
                && (tag == null || NbtUtils.compareNbt(tag, stack.getTag(), true));
        ci.setReturnValue(matches != inverted);
    }
}
