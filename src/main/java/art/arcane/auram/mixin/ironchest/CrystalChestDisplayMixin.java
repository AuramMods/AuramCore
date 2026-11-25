package art.arcane.auram.mixin.ironchest;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

@Pseudo
@Mixin(targets = "com.progwml6.ironchest.common.block.regular.entity.CrystalChestBlockEntity", remap = false)
abstract class CrystalChestDisplayMixin {
    @Unique private static final String AURAM_CRYSTAL_CHEST =
            "com.progwml6.ironchest.common.block.regular.entity.CrystalChestBlockEntity";
    @Unique private static final ResourceLocation AURAM_CRYSTAL_BLOCK = ResourceLocation.parse("ironchest:crystal_chest");
    @Unique private static final Comparator<ItemStack> AURAM_COUNT_DESCENDING =
            Comparator.comparingInt(ItemStack::getCount).reversed();
    @Unique private static MethodHandle auram$displayPacket;

    @Shadow public abstract NonNullList<ItemStack> getCurrentItems();
    @Shadow public abstract NonNullList<ItemStack> getTopItems();
    @Shadow public abstract boolean getHadStuff();
    @Shadow public abstract void setHadStuff(boolean value);

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lcom/progwml6/ironchest/common/block/regular/entity/CrystalChestBlockEntity;sortTopStacks()V"))
    private static void auram$groupDisplayStacks(@Coerce Object instance, Operation<Void> original) {
        BlockEntity chest = (BlockEntity) instance;
        if (!instance.getClass().getName().equals(AURAM_CRYSTAL_CHEST)
                || !chest.hasLevel() || !chest.getBlockState().is(BuiltInRegistries.BLOCK.get(AURAM_CRYSTAL_BLOCK))) {
            original.call(instance);
            return;
        }
        ((CrystalChestDisplayMixin) instance).auram$refreshDisplay(chest);
    }

    @Unique
    private void auram$refreshDisplay(BlockEntity chest) {
        var grouped = new IdentityHashMap<Item, List<ItemStack>>();
        var inSlotOrder = new ArrayList<ItemStack>();
        for (ItemStack incoming : getCurrentItems()) {
            if (incoming.isEmpty()) continue;
            var sameItem = grouped.computeIfAbsent(incoming.getItem(), ignored -> new ArrayList<>());
            ItemStack match = null;
            for (ItemStack candidate : sameItem) {
                if (ItemStack.isSameItemSameTags(candidate, incoming)) {
                    match = candidate;
                    break;
                }
            }
            if (match == null) {
                ItemStack copy = incoming.copy();
                sameItem.add(copy);
                inSlotOrder.add(copy);
            } else if (match.getCount() != incoming.getCount()) {
                // Match the original display's grouping of equal-size stacks.
                match.grow(incoming.getCount());
            }
        }

        boolean cleared = inSlotOrder.isEmpty() && getHadStuff();
        setHadStuff(!cleared);
        var display = getTopItems();
        Collections.fill(display, ItemStack.EMPTY);
        if (!cleared) {
            inSlotOrder.sort(AURAM_COUNT_DESCENDING);
            int slot = 0;
            for (ItemStack stack : inSlotOrder) {
                if (slot == display.size()) break;
                if (!stack.isEmpty() && stack.getCount() > 0) display.set(slot++, stack);
            }
        }
        if (chest.getLevel() != null) {
            var pos = chest.getBlockPos();
            var state = chest.getLevel().getBlockState(pos);
            chest.getLevel().sendBlockUpdated(pos, state, state, 3);
        }
        if (!cleared) auram$sendDisplay(chest);
    }

    @Unique
    private static void auram$sendDisplay(BlockEntity chest) {
        // Packet dispatch is a default method on the optional mod's interface.
        try {
            if (auram$displayPacket == null) {
                auram$displayPacket = MethodHandles.publicLookup()
                        .findVirtual(chest.getClass(), "sendTopStacksPacket", MethodType.methodType(void.class))
                        .asType(MethodType.methodType(void.class, BlockEntity.class));
            }
            auram$displayPacket.invokeExact(chest);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot send the crystal chest display", error);
        }
    }
}
