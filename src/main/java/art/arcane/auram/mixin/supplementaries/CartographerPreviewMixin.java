package art.arcane.auram.mixin.supplementaries;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$TreasureMapForEmeralds", priority = 1100)
public abstract class CartographerPreviewMixin {
    @Shadow @Final private int emeraldCost;
    @Shadow @Final private String displayName;
    @Shadow @Final private int maxUses;
    @Shadow @Final private int villagerXp;

    @Inject(method = "getOffer", at = @At("HEAD"), cancellable = true)
    private void auram$previewOfferWithoutTrader(Entity trader, RandomSource random,
                                                 CallbackInfoReturnable<MerchantOffer> callback) {
        if (trader != null) return;
        // JER asks for a preview without a villager or world. There is no destination to resolve for that map.
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        preview.setHoverName(Component.translatable(displayName));
        callback.setReturnValue(new MerchantOffer(new ItemStack(Items.EMERALD, emeraldCost),
                new ItemStack(Items.COMPASS), preview, maxUses, villagerXp, 0.2F));
    }
}
