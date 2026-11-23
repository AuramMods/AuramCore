package art.arcane.auram.client.scroll;

import net.minecraft.world.inventory.Slot;

public interface CreativeScrollViewport {
    int COLUMNS = 9;
    int ROWS = 5;
    int SLOT_SIZE = 18;
    int GRID_LEFT = 8;
    int GRID_TOP = 17;
    int GRID_WIDTH = COLUMNS * SLOT_SIZE;
    int GRID_HEIGHT = ROWS * SLOT_SIZE;
    int GRID_RIGHT = GRID_LEFT + GRID_WIDTH;
    int GRID_BOTTOM = GRID_TOP + GRID_HEIGHT;

    int auram$pixelShift();
    boolean auram$isGridSlot(Slot slot);
    Slot auram$previewSlot(int column);
}
