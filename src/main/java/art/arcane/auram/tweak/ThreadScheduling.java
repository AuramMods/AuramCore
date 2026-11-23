package art.arcane.auram.tweak;

public final class ThreadScheduling {
    public static final ThreadScheduling SETTINGS = TweakConfig.read(
            "thread-scheduling.json", ThreadScheduling.class, new ThreadScheduling());

    private boolean enabled;
    private ThreadCount threadCount = new ThreadCount();
    private ThreadPriority threadPriority = new ThreadPriority();

    private ThreadScheduling() {
    }

    public boolean enabled() {
        return enabled;
    }

    public int mainWorkers(int vanilla) {
        if (threadCount == null || threadCount.main == null) return vanilla;
        return Math.max(1, Math.min(32767, threadCount.main));
    }

    public void configureGameThread(Thread thread) {
        if (enabled && threadPriority != null) setPriority(thread, threadPriority.game);
    }

    public void configureIntegratedServer(Thread thread) {
        if (enabled && threadPriority != null) setPriority(thread, threadPriority.integratedServer);
    }

    public void configureMainWorker(Thread thread) {
        if (threadPriority != null) setPriority(thread, threadPriority.main);
    }

    public void configureIoWorker(Thread thread) {
        thread.setDaemon(true);
        if (threadPriority != null) setPriority(thread, threadPriority.io);
    }

    private static void setPriority(Thread thread, int priority) {
        thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, priority)));
    }

    private static final class ThreadCount {
        // Retained for migration. This Minecraft version has no separate Bootstrap pool.
        private int bootstrap = 1;
        private Integer main;
    }

    private static final class ThreadPriority {
        private int game = Thread.NORM_PRIORITY;
        private int bootstrap = Thread.MIN_PRIORITY;
        private int main = Thread.MIN_PRIORITY;
        private int io = Thread.MIN_PRIORITY;
        private int integratedServer = Thread.NORM_PRIORITY;
    }
}
