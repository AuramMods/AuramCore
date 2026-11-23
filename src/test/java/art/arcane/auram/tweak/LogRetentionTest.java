package art.arcane.auram.tweak;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogRetentionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void removesOnlyOldDirectArchives() throws IOException {
        Path logs = temporary.newFolder("logs").toPath();
        Instant cutoff = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
        Instant old = cutoff.minusSeconds(60);
        Path eligible = log(logs, "2020-01-01-1.log.gz", old, old);
        Path usedRecently = log(logs, "2020-01-01-2.log.gz", old, cutoff.plusSeconds(1));
        Path boundary = log(logs, "2020-01-01-3.log.gz", cutoff, old);
        Path current = log(logs, "latest.log", old, old);
        Path doubleDigit = log(logs, "2020-01-01-10.log.gz", old, old);
        Path subdirectory = Files.createDirectory(logs.resolve("2020-01-01-4.log.gz"));
        Path nested = log(subdirectory, "2020-01-01-5.log.gz", old, old);
        Path symlink = Files.createSymbolicLink(logs.resolve("2020-01-01-6.log.gz"), nested);

        assertEquals(1, LogRetention.removeArchives(logs, cutoff));
        assertFalse(Files.exists(eligible));
        for (Path kept : new Path[]{usedRecently, boundary, current, doubleDigit, subdirectory, nested, symlink}) {
            assertTrue(kept.toString(), Files.exists(kept));
        }
        assertEquals(0, LogRetention.removeArchives(logs, cutoff));
        assertEquals(0, LogRetention.removeArchives(logs.resolve("missing"), cutoff));
    }

    private static Path log(Path directory, String filename, Instant modified, Instant accessed) throws IOException {
        Path path = Files.createFile(directory.resolve(filename));
        Files.getFileAttributeView(path, BasicFileAttributeView.class)
                .setTimes(FileTime.from(modified), FileTime.from(accessed), null);
        return path;
    }
}
