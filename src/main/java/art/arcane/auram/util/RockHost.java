package art.arcane.auram.util;

import java.util.Locale;
import java.util.Set;

/** The host belongs to an ore variant, not to every ore from its mod. */
public enum RockHost {
    STONE("stone", "rock_ore"),
    NETHERRACK("netherrack", "rock_netherrack"),
    END_STONE("end_stone", "rock_end_stone"),
    DEEPSLATE("deepslate", "rock_deepslate");

    public final String texture;
    public final String model;

    RockHost(String texture, String model) {
        this.texture = texture;
        this.model = model;
    }

    public static RockHost detect(String orePath, String texturePath) {
        RockHost explicit = fromPath(orePath);
        return explicit != STONE ? explicit : fromPath(texturePath);
    }

    public static RockHost fromPath(String path) {
        Set<String> words = Set.copyOf(java.util.Arrays.asList(path.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")));
        if (words.contains("deepslate") || words.contains("deep") && words.contains("slate")) return DEEPSLATE;
        if (words.contains("netherrack") || words.contains("nether")) return NETHERRACK;
        if (words.contains("endstone") || words.contains("end")) return END_STONE;
        return STONE;
    }
}
