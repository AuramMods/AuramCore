package art.arcane.auram.mixin.worldedit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.forge.internal.ForgeTransmogrifier", remap = false)
public abstract class ForgePropertyCacheMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/google/common/cache/CacheBuilder;build(Lcom/google/common/cache/CacheLoader;)Lcom/google/common/cache/LoadingCache;"))
    private static LoadingCache<?, ?> auram$cacheByPropertyIdentity(CacheBuilder<?, ?> builder,
            CacheLoader<?, ?> loader, Operation<LoadingCache<?, ?>> original) {
        // A DirectionProperty can compare equal to an EnumProperty, but WorldEdit encodes them differently.
        return original.call(builder.weakKeys(), loader);
    }
}
