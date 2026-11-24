package art.arcane.auram.mixin.botania;

import art.arcane.auram.vapor.BotaniaVapor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.mana.ManaPoolBlock;

@Mixin(value = ManaPoolBlockEntity.class, remap = false)
abstract class ManaPoolVaporMixin {
    @WrapMethod(method = "receiveMana")
    private void auram$vaporOnTransfer(int amount, Operation<Void> original) {
        ManaPoolBlockEntity pool = (ManaPoolBlockEntity) (Object) this;
        if (amount == 0 || pool.getMaxMana() <= 0 || !(pool.getLevel() instanceof ServerLevel level)) {
            original.call(amount);
            return;
        }
        int before = pool.getCurrentMana();
        original.call(amount);

        int mana = pool.getCurrentMana();
        boolean creativeOutput = amount < 0 && pool.getBlockState().getBlock() instanceof ManaPoolBlock block
                && block.variant == ManaPoolBlock.Variant.CREATIVE;
        // A Mana Void can consume deliveries into an already-full pool without changing its stored mana.
        boolean voidInput = amount > 0 && mana == pool.getMaxMana() && !pool.isFull();
        if (before != mana || creativeOutput || voidInput) {
            BotaniaVapor.poolTransfer(level, pool.getBlockPos(), mana, pool.getMaxMana());
        }
    }
}
