package art.arcane.auram.mixin.ftbserver;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.UUID;

@Pseudo
@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
abstract class ForceLoadedChunksMixin {
    @Shadow private Map<ResourceKey<Level>, Long2ObjectMap<UUID>> forceLoadedChunkCache;

    @WrapOperation(method = "getForceLoadedChunks(Lnet/minecraft/resources/ResourceKey;)Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;",
            at = @At(value = "INVOKE", target =
                    "Ldev/ftb/mods/ftbchunks/data/ClaimedChunkManagerImpl;getForceLoadedChunks()Ljava/util/Map;"))
    private Map<ResourceKey<Level>, Long2ObjectMap<UUID>> auram$readExistingForceLoadedCache(
            ClaimedChunkManagerImpl manager, Operation<Map<ResourceKey<Level>, Long2ObjectMap<UUID>>> original) {
        // The caller immediately looks up a dimension and returns that entry.
        // Use the mod's cache without allocating a read-only view of the outer map.
        var cached = forceLoadedChunkCache;
        if (cached != null && manager.getClass() == ClaimedChunkManagerImpl.class) return cached;
        return original.call(manager);
    }
}
