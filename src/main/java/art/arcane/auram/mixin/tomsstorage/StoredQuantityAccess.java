package art.arcane.auram.mixin.tomsstorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="com.tom.storagemod.util.StoredItemStack",remap=false)
public interface StoredQuantityAccess {
    @Invoker("getQuantity") long auram$quantity();
}
