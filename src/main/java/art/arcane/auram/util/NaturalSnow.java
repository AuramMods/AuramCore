package art.arcane.auram.util;

import art.arcane.auram.Auram;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;

public final class NaturalSnow {
    // Biomes using a custom snow feature or surface rule can opt in with a datapack.
    private static final TagKey<Biome> SNOW_COVER =
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(Auram.MODID, "natural_snow_cover"));
    private static final int TOP_LAYER = GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal();

    private NaturalSnow() {}

    public static boolean canAccumulate(Holder<Biome> biome, BlockPos pos) {
        if (biome.is(SNOW_COVER)) return true;

        Biome climate = biome.value();
        // World generation uses the position's normal temperature, including altitude.
        if (!climate.coldEnoughToSnow(pos)) return false;

        var steps = climate.getGenerationSettings().features();
        if (steps.size() <= TOP_LAYER) return false;
        for (var placed : steps.get(TOP_LAYER)) {
            if (placed.value().feature().value().feature() == Feature.FREEZE_TOP_LAYER) return true;
        }
        return false;
    }
}
