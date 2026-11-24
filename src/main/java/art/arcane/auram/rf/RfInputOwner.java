package art.arcane.auram.rf;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Associates a shared energy-capability implementation with its receiving block entity. */
public interface RfInputOwner {
    void auram$setRfInputOwner(BlockEntity machine);
}
