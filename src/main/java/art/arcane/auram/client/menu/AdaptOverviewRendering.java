package art.arcane.auram.client.menu;

public final class AdaptOverviewRendering {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private AdaptOverviewRendering() {}

    public static boolean active() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void drawNativeControls(Runnable render) {
        if (active()) {
            render.run();
            return;
        }
        ACTIVE.set(true);
        try {
            render.run();
        } finally {
            ACTIVE.remove();
        }
    }
}
