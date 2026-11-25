package art.arcane.auram.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.Map;

/** Preserve insertion order for unbounded entity queries restricted to one exact type. */
@Mixin(EntityLookup.class)
abstract class EntityLookupTypeMixin<T extends EntityAccess> {
    @Shadow @Final private Int2ObjectMap<T> byId;
    @Unique private Map<EntityType<?>, Int2ObjectLinkedOpenHashMap<T>> auram$types;

    @Inject(method = "getEntities", at = @At("HEAD"), cancellable = true)
    private <U extends T> void auram$queryExactType(EntityTypeTest<T, U> test,
                                                  AbortableIterationConsumer<U> consumer, CallbackInfo ci) {
        if (!((Object) test instanceof EntityType<?> type) || byId.getClass() != Int2ObjectLinkedOpenHashMap.class) {
            return;
        }
        if (auram$types == null) auram$types = new IdentityHashMap<>();
        var entries = auram$types.get(type);
        if (entries == null) {
            entries = new Int2ObjectLinkedOpenHashMap<>();
            for (T entity : byId.values()) {
                if (entity instanceof Entity value && value.getType() == type) entries.put(entity.getId(), entity);
            }
            auram$types.put(type, entries);
        }
        for (T entity : entries.values()) {
            U match = test.tryCast(entity);
            if (match != null && consumer.accept(match).shouldAbort()) break;
        }
        ci.cancel();
    }

    @WrapOperation(method = "add", at = @At(value = "INVOKE", target =
            "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;put(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object auram$indexAddedEntity(Int2ObjectMap<T> entities, int id, Object entry,
                                          Operation<Object> original) {
        Object previous = original.call(entities, id, entry);
        if (auram$types != null && previous != entry) {
            if (previous != null) {
                // Replacing an ID retains its old position in the main map.
                auram$types.clear();
            } else if (entry instanceof Entity entity) {
                var sameType = auram$types.get(entity.getType());
                if (sameType != null) sameType.put(id, entities.get(id));
            }
        }
        return previous;
    }

    @WrapOperation(method = "remove", at = @At(value = "INVOKE", target =
            "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;remove(I)Ljava/lang/Object;"))
    private Object auram$unindexRemovedEntity(Int2ObjectMap<T> entities, int id, Operation<Object> original) {
        Object removed = original.call(entities, id);
        if (auram$types != null && removed instanceof Entity entity) {
            var sameType = auram$types.get(entity.getType());
            if (sameType != null) sameType.remove(id);
        }
        return removed;
    }
}
