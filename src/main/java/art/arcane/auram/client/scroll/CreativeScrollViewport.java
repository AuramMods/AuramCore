package art.arcane.auram.client.scroll;

import net.minecraft.world.inventory.Slot;

public interface CreativeScrollViewport {
    int auram$pixelShift();
    boolean auram$isGridSlot(Slot slot);
    Slot auram$previewSlot(int column);
}
