package art.arcane.auram.tweak;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class TweakConfig {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private TweakConfig() {
    }

    public static <T> T read(String filename, Class<T> type, T defaults) {
        Path config = FMLPaths.CONFIGDIR.get().resolve("auram").resolve(filename);
        try (var reader = Files.newBufferedReader(config)) {
            T settings = GSON.fromJson(reader, type);
            return settings == null ? defaults : settings;
        } catch (NoSuchFileException ignored) {
            return defaults;
        } catch (IOException | JsonParseException error) {
            LOGGER.warn("Could not read {}", config, error);
            return defaults;
        }
    }
}
