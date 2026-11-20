package art.arcane.auram.mixin.minecraft;

import net.minecraft.core.Registry;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldDimensions.class)
public abstract class SuperflatWorldDimensionsMixin {
    @Shadow public abstract ChunkGenerator overworld();

    @ModifyVariable(method = "bake", at = @At("HEAD"), argsOnly = true)
    private Registry<LevelStem> auram$preserveSuperflatGenerator(Registry<LevelStem> datapackDimensions) {
        if (!(overworld() instanceof FlatLevelSource flatGenerator)) return datapackDimensions;

        LevelStem datapackOverworld = datapackDimensions.get(LevelStem.OVERWORLD);
        if (datapackOverworld == null) return datapackDimensions;

        // Baking prefers the datapack stem. Keep its dimension type while preserving the selected flat settings.
        return WorldDimensions.withOverworld(datapackDimensions, datapackOverworld.type(), flatGenerator);
    }
}
