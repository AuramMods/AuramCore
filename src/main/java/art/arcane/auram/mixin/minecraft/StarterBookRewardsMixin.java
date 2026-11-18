package art.arcane.auram.mixin.minecraft;

import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AdvancementRewards.class)
public abstract class StarterBookRewardsMixin {
    @Unique private static final ResourceLocation AURAM_TERRAMITY_BOOK = ResourceLocation.tryParse("minecraft:gameplay/terramity_guidebook_loot");
    @Unique private static final ResourceLocation AURAM_CRYSTALOPEDIA = ResourceLocation.tryParse("crystalcraft_unlimited_java:gameplay/book");

    @Redirect(method = "grant", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/storage/loot/LootDataManager;getLootTable(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/level/storage/loot/LootTable;"))
    private LootTable auram$skipStarterBookRewards(LootDataManager loot, ResourceLocation table) {
        // Suppress only the two dedicated book rewards. Advancement progress and
        // Crystalcraft's recipe unlocks still use the original grant method.
        if (AURAM_TERRAMITY_BOOK.equals(table) || AURAM_CRYSTALOPEDIA.equals(table)) {
            return LootTable.EMPTY;
        }
        return loot.getLootTable(table);
    }
}
