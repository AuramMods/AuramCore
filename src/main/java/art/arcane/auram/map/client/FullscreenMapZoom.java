package art.arcane.auram.map.client;

/** The displayed zoom uses fractions of a GUI pixel while FTB lays out integer-sized regions. */
public final class FullscreenMapZoom {
    private double size;
    private double startSize;
    private int targetSize;
    private double cursorX;
    private double cursorY;
    private long startedAt;

    public FullscreenMapZoom(int size) {
        this.size = this.targetSize = size;
    }

    public void retarget(int size, double mouseX, double mouseY, long now) {
        startSize = this.size;
        targetSize = size;
        cursorX = mouseX;
        cursorY = mouseY;
        startedAt = now;
    }

    public boolean advance(long now) {
        if (size == targetSize) return false;
        double progress = MapAnimation.easeOut(now - startedAt);
        double next = progress == 1 ? targetSize : startSize * Math.pow(targetSize / startSize, progress);
        if (next == size) return false;
        size = next;
        return true;
    }

    public int layoutSize() {
        // Scaling up from the integer layout keeps FTB's normal widget culling conservative.
        return Math.max(1, (int) size);
    }

    public double scale() {
        return size / layoutSize();
    }

    public double size() {
        return size;
    }

    public double cursorX() {
        return cursorX;
    }

    public double cursorY() {
        return cursorY;
    }

    public double regionAt(int firstRegion, double scroll, double screenOffset) {
        return firstRegion + (scroll + screenOffset / scale()) / layoutSize();
    }

    public double scrollFor(int firstRegion, double region, double screenOffset) {
        return (region - firstRegion) * layoutSize() - screenOffset / scale();
    }
}
