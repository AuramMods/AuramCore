package art.arcane.auram.client;

import art.arcane.auram.tweak.TweakConfig;

public final class TransitionSettings {
    public static final TransitionSettings INSTANCE = TweakConfig.read("skip-transitions.json",
            TransitionSettings.class, new TransitionSettings());

    private boolean enabled;
    private boolean removeTitleScreenFade = true;
    private boolean removeSplashFade = true;
    private boolean removeToastsSlide = true;
    private boolean removeToastsSFX = true;

    public boolean skipTitleFade() {
        return enabled && removeTitleScreenFade;
    }

    public boolean skipSplashFade() {
        return enabled && removeSplashFade;
    }

    public boolean skipToastSlide() {
        return enabled && removeToastsSlide;
    }

    public boolean skipToastSounds() {
        return enabled && removeToastsSFX;
    }
}
