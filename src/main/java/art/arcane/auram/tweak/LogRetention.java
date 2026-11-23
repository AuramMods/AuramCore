package art.arcane.auram.tweak;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

public final class LogRetention {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern ARCHIVE_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d\\.log\\.gz");

    private LogRetention() {
    }

    public static void onStartup() {
        Settings settings = TweakConfig.read("log-retention.json", Settings.class, new Settings());
        if (!settings.enabled) return;

        Path logs = FMLPaths.GAMEDIR.get().resolve("logs");
        Instant cutoff = Instant.now().minus(Math.max(0, settings.daysOld), ChronoUnit.DAYS);
        int removed = removeArchives(logs, cutoff);
        if (removed > 0 && !settings.silent) {
            LOGGER.info("Removed {} old compressed log archives", removed);
        }
    }

    static int removeArchives(Path logs, Instant cutoff) {
        if (!Files.isDirectory(logs, LinkOption.NOFOLLOW_LINKS)) return 0;

        int removed = 0;
        try (var paths = Files.newDirectoryStream(logs, path -> ARCHIVE_NAME.matcher(path.getFileName().toString()).matches())) {
            for (Path path : paths) {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isRegularFile()
                            && attributes.creationTime().toInstant().isBefore(cutoff)
                            && attributes.lastModifiedTime().toInstant().isBefore(cutoff)
                            && attributes.lastAccessTime().toInstant().isBefore(cutoff)
                            && Files.deleteIfExists(path)) {
                        removed++;
                    }
                } catch (NoSuchFileException ignored) {
                    // Rotation or another cleanup already removed this file.
                } catch (IOException error) {
                    LOGGER.debug("Could not remove old log archive {}", path, error);
                }
            }
        } catch (IOException | DirectoryIteratorException error) {
            LOGGER.debug("Could not scan log archives in {}", logs, error);
        }
        return removed;
    }

    private static final class Settings {
        private boolean enabled;
        private int daysOld = 14;
        private boolean silent;
    }
}
