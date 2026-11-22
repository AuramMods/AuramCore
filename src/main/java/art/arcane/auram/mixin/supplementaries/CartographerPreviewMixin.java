package art.arcane.auram.mixin.supplementaries;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$TreasureMapForEmeralds")
public abstract class CartographerPreviewMixin {
    @Shadow @Final private int emeraldCost;
    @Shadow @Final private String displayName;
    @Shadow @Final private int maxUses;
    @Shadow @Final private int villagerXp;

    @WrapMethod(method = "getOffer")
    private MerchantOffer auram$previewOfferWithoutTrader(Entity trader, RandomSource random,
                                                          Operation<MerchantOffer> original) {
        if (trader != null) return original.call(trader, random);
        // JER asks for a preview without a villager or world. There is no destination to resolve for that map.
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        preview.setHoverName(Component.translatable(displayName));
        return new MerchantOffer(new ItemStack(Items.EMERALD, emeraldCost),
                new ItemStack(Items.COMPASS), preview, maxUses, villagerXp, 0.2F);
    }
}
