package art.arcane.auram.mixin.supplementaries;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.entities.trades.RandomAdventurerMapListing", remap = false)
public abstract class AdventurerMapPreviewMixin {
    @Shadow @Final private Item emerald;
    @Shadow @Final private int priceMin;
    @Shadow @Final private int priceMax;
    @Shadow @Final private ItemStack priceSecondary;
    @Shadow @Final private int maxTrades;
    @Shadow @Final private float priceMult;

    @WrapMethod(method = "m_213663_", remap = false)
    private MerchantOffer auram$previewOfferWithoutTrader(Entity trader, RandomSource random,
                                                          Operation<MerchantOffer> original) {
        if (trader != null) return original.call(trader, random);
        // JER requests a preview without the server level needed to find an adventure-map destination.
        ItemStack preview = new ItemStack(Items.FILLED_MAP);
        preview.setHoverName(Component.translatable("filled_map.adventure"));
        int price = priceMax + random.nextInt(priceMax - priceMin + 1);
        int xp = (int) (72.0F / maxTrades);
        return new MerchantOffer(new ItemStack(emerald, price), priceSecondary.copy(), preview, maxTrades, xp, priceMult);
    }
}
