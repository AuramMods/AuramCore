package art.arcane.auram.mixin.dreamtinker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

import java.util.IdentityHashMap;

@Pseudo
@Mixin(targets = "org.dreamtinker.dreamtinker.library.worldgen.ScatterReplaceOreFeature", remap = false)
public abstract class ScatterReplaceOreMixin {
    @Group(name = "auram$scatterPredicate", min = 1, max = 1)
    @WrapOperation(method = "m_142674_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/RuleTest;m_213865_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Z"),
            require = 0)
    private boolean auram$reuseTagsRuntime(RuleTest rule, BlockState state, RandomSource random,
                                           Operation<Boolean> original,
                                           @Share("auram$tagResults") LocalRef<IdentityHashMap<RuleTest, Reference2ByteOpenHashMap<BlockState>>> shared) {
        return auram$test(rule, state, random, original, shared);
    }

    @Group(name = "auram$scatterPredicate", min = 1, max = 1)
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/RuleTest;test(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Z"),
            require = 0)
    private boolean auram$reuseTagsDevelopment(RuleTest rule, BlockState state, RandomSource random,
                                               Operation<Boolean> original,
                                               @Share("auram$tagResults") LocalRef<IdentityHashMap<RuleTest, Reference2ByteOpenHashMap<BlockState>>> shared) {
        return auram$test(rule, state, random, original, shared);
    }

    @Unique
    private static boolean auram$test(RuleTest rule, BlockState state, RandomSource random,
                                      Operation<Boolean> original,
                                      LocalRef<IdentityHashMap<RuleTest, Reference2ByteOpenHashMap<BlockState>>> shared) {
        if (!rule.getClass().getName().equals("org.dreamtinker.dreamtinker.library.worldgen.TagAndTagRuleTest")) {
            return original.call(rule, state, random);
        }
        // This exact RuleTest only reads the two block tags. Other tests may consume RNG.
        var memo = shared.get();
        if (memo == null) {
            memo = new IdentityHashMap<>();
            shared.set(memo);
        }
        var states = memo.computeIfAbsent(rule, ignored -> new Reference2ByteOpenHashMap<>());
        byte value = states.getByte(state);
        if (value != 0) return value == 2;
        boolean result = original.call(rule, state, random);
        states.put(state, (byte) (result ? 2 : 1));
        return result;
    }
}
