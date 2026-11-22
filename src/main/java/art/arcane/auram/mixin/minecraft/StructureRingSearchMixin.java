package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.DeferredRingSearch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkGeneratorStructureState.class)
public abstract class StructureRingSearchMixin implements DeferredRingSearch.Access {
    @Shadow public abstract void ensureStructuresGenerated();

    @Unique private final Map<ConcentricRingsStructurePlacement, DeferredRingSearch> auram$ringSearches =
            new ConcurrentHashMap<>();

    @WrapOperation(method = "generateRingPositions", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkPos> auram$deferBiomeSearch(Supplier<ChunkPos> search, Executor executor,
            Operation<CompletableFuture<ChunkPos>> original, @Local(index = 17) int x, @Local(index = 18) int z,
            @Share("auramRingSearch") LocalRef<DeferredRingSearch> shared) {
        if (Boolean.getBoolean("auram.worldgen.eagerStructureRings")) return original.call(search, executor);
        var plan = shared.get();
        if (plan == null) {
            plan = new DeferredRingSearch();
            shared.set(plan);
        }
        // Keep vanilla's supplier, per-candidate random fork, and ordered completion list.
        return plan.defer(x, z, search, executor);
    }

    @Inject(method = "generateRingPositions", at = @At("RETURN"))
    private void auram$indexRingSearch(Holder<StructureSet> structures, ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> callback,
            @Share("auramRingSearch") LocalRef<DeferredRingSearch> shared) {
        var plan = shared.get();
        if (plan != null) auram$ringSearches.put(placement, plan);
    }

    @Inject(method = "getRingPositionsFor", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private void auram$resolveFullRingList(ConcentricRingsStructurePlacement placement,
                                          CallbackInfoReturnable<List<ChunkPos>> callback) {
        var plan = auram$ringSearches.get(placement);
        if (plan != null) plan.startAll();
    }

    @Override
    public Boolean auram$containsRingChunk(ConcentricRingsStructurePlacement placement, int x, int z) {
        ensureStructuresGenerated();
        var plan = auram$ringSearches.get(placement);
        return plan == null ? null : plan.contains(x, z);
    }
}
