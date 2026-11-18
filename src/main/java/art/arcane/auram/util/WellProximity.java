package art.arcane.auram.util;

import com.sammy.malum.common.block.curiosities.weeping_well.VoidConduitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** The periodic Malum proximity query, without requesting or waiting for chunks. */
public final class WellProximity {
    private WellProximity() { }

    /** null means incomplete: retain the previous proximity state and retry next scan. */
    public static Boolean find(ServerLevel level, BlockPos center) {
        // Match Lodestone's radius-8 box, including its asymmetric +1.5/+0.5 offsets.
        AABB box = new AABB(center.getX() - 6.5, center.getY() - 6.5, center.getZ() - 6.5,
                center.getX() + 8.5, center.getY() + 8.5, center.getZ() + 8.5);
        int minX = SectionPos.blockToSectionCoord(center.getX() - 7);
        int maxX = SectionPos.blockToSectionCoord(center.getX() + 7);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - 7);
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + 7);
        // Preserve the original X-then-Z traversal and each chunk's position order.
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return null;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                            && !level.isOutsideBuildHeight(pos)
                            // Match Level.getBlockEntity's creation policy, including
                            // removed entries and pending saved block-entity tags.
                            && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) instanceof VoidConduitBlockEntity) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
