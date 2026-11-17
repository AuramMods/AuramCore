package art.arcane.auram.mixin.tomsstorage;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/** Reuse the lookup already performed earlier in the same packet-entry write. */
@Pseudo
@Mixin(targets="com.tom.storagemod.util.TerminalSyncManager",remap=false)
public abstract class TerminalIdMixin {
    @Shadow private Object2IntMap<?> idMap;

    @Inject(method="write",at=@At(value="INVOKE",target=
            "Lit/unimi/dsi/fastutil/objects/Object2IntMap;computeIfAbsent(Ljava/lang/Object;Lit/unimi/dsi/fastutil/objects/Object2IntFunction;)I"),
            cancellable=true,locals=LocalCapture.CAPTURE_FAILHARD)
    private void auram$reuseKnownId(FriendlyByteBuf output,@Coerce Object entry,CallbackInfo ci,
                                  ItemStack stack,Item item,CompoundTag shareTag,byte flags,boolean newItem,int knownId) {
        // Only a flag byte is written between the original getInt and this
        // point. A known ID has no item definition/NBT tail, just its quantity.
        // Preserve missing/ID-zero entries and custom map behavior unchanged.
        if(!newItem && knownId!=0 && idMap.getClass()==Object2IntOpenHashMap.class) {
            output.writeVarInt(knownId);
            StoredQuantityAccess quantity=(StoredQuantityAccess)entry;
            if(quantity.auram$quantity()!=0) output.writeVarLong(quantity.auram$quantity());
            ci.cancel();
        }
    }
}
