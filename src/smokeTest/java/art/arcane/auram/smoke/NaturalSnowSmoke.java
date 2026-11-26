package art.arcane.auram.smoke;

import art.arcane.auram.util.NaturalSnow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Supplier;

final class NaturalSnowSmoke {
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> taiga = biomes.getHolderOrThrow(Biomes.TAIGA);
        Holder<Biome> snowyPlains = biomes.getHolderOrThrow(Biomes.SNOWY_PLAINS);
        Holder<Biome> coldWithoutSnow = Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(true)
                .temperature(0).downfall(0.5F).specialEffects(snowyPlains.value().getSpecialEffects())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
        var snowyChunk = seedBiomes(level, 0, snowyPlains);
        var taigaChunk = seedBiomes(level, 3, taiga);
        var snowPos = surface(level, snowyChunk);
        var taigaPos = surface(level, taigaChunk);
        check(!taiga.value().coldEnoughToSnow(taigaPos), "ordinary taiga surface climate");
        check(NaturalSnow.canAccumulate(taiga, taigaPos.atY(320)), "naturally snowy high-altitude taiga");
        check(coldWithoutSnow.value().coldEnoughToSnow(snowPos), "cold biome climate");
        check(!NaturalSnow.canAccumulate(coldWithoutSnow, snowPos), "cold biome without snow generation");

        int season = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "season set mid_winter");
        check(season > 0, "set Serene Seasons winter");
        level.setWeatherParameters(0, 6000, true, false);
        level.setRainLevel(1.0F);
        level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(8, server);
        check(level.isRaining() && taiga.value().shouldSnow(level, taigaPos), "winter can snow in taiga");
        for (int i = 0; i < 2048; i++) {
            level.tickChunk(taigaChunk, 0);
            level.tickChunk(snowyChunk, 0);
        }
        check(countSnow(level, taigaChunk, taigaPos.getY()) == 0, "winter snow stays off ordinary taiga terrain");
        check(countSnow(level, snowyChunk, snowPos.getY()) > 50, "snowy plains still acquire snow in a storm");
        var growth = Class.forName("com.ordana.immersive_weathering.util.TemperatureManager")
                .getMethod("snowGrowthCanGrowSnowyBlock", BlockPos.class, Supplier.class);
        check((boolean) growth.invoke(null, snowPos, (Supplier<Holder<Biome>>) () -> snowyPlains), "snow coats naturally snowy blocks");
        check(!(boolean) growth.invoke(null, snowPos, (Supplier<Holder<Biome>>) () -> coldWithoutSnow),
                "snow coating checks biome generation");
    }

    private static LevelChunk seedBiomes(ServerLevel level, int chunkX, Holder<Biome> biome) {
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.getChunk(x, z).fillBiomesFromNoise((bx, by, bz, sampler) -> biome,
                        level.getChunkSource().randomState().sampler());
            }
        }
        return level.getChunk(chunkX, 0);
    }

    private static BlockPos surface(ServerLevel level, LevelChunk chunk) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, chunk.getPos().getMiddleBlockPosition(0));
    }

    private static int countSnow(ServerLevel level, LevelChunk chunk, int y) {
        int count = 0;
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.SNOW)) count++;
            }
        }
        return count;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
