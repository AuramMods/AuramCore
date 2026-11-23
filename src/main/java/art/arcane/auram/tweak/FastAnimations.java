package art.arcane.auram.tweak;

public final class FastAnimations {
    private static final Settings SETTINGS = TweakConfig.read("fast-anim.json", Settings.class, new Settings());

    private FastAnimations() {
    }

    public static boolean enabled() {
        return SETTINGS.enabled;
    }

    private static final class Settings {
        private boolean enabled;
    }
}
