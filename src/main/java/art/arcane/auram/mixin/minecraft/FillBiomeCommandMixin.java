package art.arcane.auram.mixin.minecraft;

import art.arcane.auram.util.BiomeFillBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(FillBiomeCommand.class)
public abstract class FillBiomeCommandMixin {
    @Redirect(method = "fill", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"))
    private static void auram$boundCommandRewrite(ChunkAccess chunk, BiomeResolver resolver, Climate.Sampler sampler,
                                                CommandSourceStack source, BlockPos from, BlockPos to,
                                                Holder.Reference<Biome> biome, Predicate<Holder<Biome>> filter) {
        // Preserve vanilla's resolver, chunk validation, result count, dirty
        // marking and resendBiomesForChunks call. Only constrain this rewrite.
        BiomeFillBounds.fill(chunk, resolver, sampler, from.getY(), to.getY());
    }
}
