package art.arcane.auram.vapor.client;

import art.arcane.auram.compat.botania.EndoflameBurnState;
import art.arcane.auram.field.VoxelGrid.Position;
import art.arcane.auram.light.LightClient;
import art.arcane.auram.vapor.BotaniaVapor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;
import vazkii.botania.common.block.flower.generating.EndoflameBlockEntity;
import vazkii.botania.common.entity.ManaBurstEntity;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.auram.field.VoxelGrid.BLOCKS;
import static art.arcane.auram.field.VoxelGrid.SECTION;

public final class BotaniaEffects {
    private static final int MAX_BURSTS = 128;
    private static final float BURST_VAPOR = 0.03f;
    private static final int BURST_ALPHA = Math.round(0.125f * 255);
    private static final int ENDOFLAME_LIGHT = Math.round(0.25f * 255) << 24 | 0xFFAD3B;
    private static final EntityTypeTest<Entity, ManaBurstEntity> BURSTS = EntityTypeTest.forClass(ManaBurstEntity.class);

    private BotaniaEffects() {}

    static void addVapor(ClientLevel level, AABB window, List<VaporVolume.Source> sources) {
        for (ManaBurstEntity burst : bursts(level, window)) {
            sources.add(new VaporVolume.Source(VaporClient.position(burst.blockPosition()), BURST_VAPOR, 0));
        }
    }

    public static void collectLights(ClientLevel level, Position origin, Int2IntOpenHashMap colors) {
        int side = BLOCKS.side();
        var window = new AABB(origin.x(), origin.y(), origin.z(), origin.x() + side, origin.y() + side, origin.z() + side);
        for (ManaBurstEntity burst : bursts(level, window)) {
            LightClient.putSource(origin, burst.blockPosition(), BURST_ALPHA << 24 | (burst.getColor() & 0xFFFFFF), colors);
        }

        int firstX = Math.floorDiv(origin.x(), SECTION);
        int firstZ = Math.floorDiv(origin.z(), SECTION);
        for (int x = firstX; x < firstX + side / SECTION; x++) {
            for (int z = firstZ; z < firstZ + side / SECTION; z++) {
                var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity.isRemoved()) continue;
                    if (entity instanceof ManaPoolBlockEntity pool) {
                        float fill = BotaniaVapor.poolFill(pool.getCurrentMana(), pool.getMaxMana());
                        int brightness = Math.round(fill * 0.5f * 255);
                        LightClient.putSource(origin, pool.getBlockPos(),
                                brightness << 24 | ManaPoolBlockEntity.PARTICLE_COLOR, colors);
                    } else if (entity instanceof EndoflameBlockEntity flower
                            && entity instanceof EndoflameBurnState state && state.auram$isBurning()) {
                        LightClient.putSource(origin, flower.getEffectivePos(), ENDOFLAME_LIGHT, colors);
                    }
                }
            }
        }
    }

    private static List<ManaBurstEntity> bursts(ClientLevel level, AABB window) {
        var found = new ArrayList<ManaBurstEntity>();
        level.getEntities(BURSTS, window, burst -> burst.isAlive() && !burst.isFake(), found, MAX_BURSTS);
        return found;
    }
}
