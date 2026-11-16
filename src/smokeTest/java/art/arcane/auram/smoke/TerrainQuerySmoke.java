package art.arcane.auram.smoke;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class TerrainQuerySmoke {
    public static void run(MinecraftServer server) throws Exception {
        List<String> results=new ArrayList<>();
        for(String id:new String[]{"ae2:meteorite","malum:weeping_well","nyctophobia:mushroom_structure",
                "minecraft:village_plains","minecraft:nether_fossil"}) {
            ServerLevel level=id.endsWith("nether_fossil") ? server.getLevel(Level.NETHER) : server.overworld();
            var generator=level.getChunkSource().getGenerator();
            var state=level.getChunkSource().randomState();
            Structure structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(id));
            if(structure==null) throw new AssertionError("Missing fixture structure "+id);
            int accepted=0,pieces=0;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            TerrainQueryProbe.start();
            long start=System.nanoTime();
            for(int i=0;i<12;i++) {
                // Neighboring chunks, one attempt each: no artificial replay of
                // the same attempt to inflate prospective cache hit rates.
                TerrainQueryProbe.nextAttempt();
                Structure.GenerationContext context=new Structure.GenerationContext(level.registryAccess(),generator,
                        generator.getBiomeSource(),state,server.getStructureManager(),level.getSeed(),
                        new ChunkPos(80+i,80),level,biome->true);
                var stub=structure.findValidGenerationPoint(context);
                digest.update((i+":"+stub.map(s->s.position().toShortString()).orElse("absent")+";").getBytes(StandardCharsets.UTF_8));
                if(stub.isPresent()) {
                    accepted++;
                    var container=stub.get().getPiecesBuilder().build();
                    pieces+=container.pieces().size();
                    digest.update(canonical(container.save(StructurePieceSerializationContext.fromLevel(level))).getBytes(StandardCharsets.UTF_8));
                }
                digest.update(Long.toString(context.random().nextLong()).getBytes(StandardCharsets.UTF_8));
            }
            double millis=(System.nanoTime()-start)/1e6;
            TerrainQueryProbe.Trace trace=TerrainQueryProbe.stop();
            String result=String.format(java.util.Locale.ROOT,
                    "{\"structure\":\"%s\",\"attempts\":12,\"accepted\":%d,\"pieces\":%d,\"elapsed_ms\":%.3f,\"fingerprint\":\"%s\",\"trace\":%s}",id,accepted,pieces,millis,HexFormat.of().formatHex(digest.digest()),trace.json());
            results.add(result);
            LogUtils.getLogger().info("[AURAM TERRAIN] {}",result);
        }
        Files.writeString(Path.of("terrain-query-trace.json"),"{\"results\":["+String.join(",",results)+"]}\n");
    }
    private static String canonical(Tag tag) {
        if(tag instanceof CompoundTag compound) {
            StringBuilder result=new StringBuilder("{");
            compound.getAllKeys().stream().sorted().forEach(key->result.append(key.length()).append(':').append(key).append(canonical(compound.get(key))));
            return result.append('}').toString();
        }
        if(tag instanceof ListTag list) {
            StringBuilder result=new StringBuilder("[");
            for(Tag child:list) result.append(canonical(child)).append(';');
            return result.append(']').toString();
        }
        return tag.getId()+":"+tag;
    }
}
