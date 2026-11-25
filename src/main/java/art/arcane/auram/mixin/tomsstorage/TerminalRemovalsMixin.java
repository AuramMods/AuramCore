package art.arcane.auram.mixin.tomsstorage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "com.tom.storagemod.util.TerminalSyncManager", remap = false)
abstract class TerminalRemovalsMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target =
            "Lit/unimi/dsi/fastutil/objects/Object2LongMap;forEach(Ljava/util/function/BiConsumer;)V"))
    private void auram$skipKnownEmptyRemovalPass(Object2LongMap<?> previous,
                                                BiConsumer<?, ?> visit,
                                                Operation<Void> original,
                                                Map<?, ?> current, ServerPlayer player, Consumer<?> extra,
                                                @Local Set<?> found) {
        // The forward pass adds a unique key only after finding a nonzero
        // quantity in previous. Equal cardinality proves all old keys were found.
        Class<?> currentType = current.getClass();
        if (previous.getClass() != Object2LongOpenHashMap.class
                || (currentType != HashMap.class && currentType != LinkedHashMap.class)
                || found.size() != previous.size()) {
            original.call(previous, visit);
        }
    }
}
