package art.arcane.auram.map.client;

import art.arcane.auram.map.SurfaceTile;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

final class SurfaceTileCache {
    private final Path root;

    SurfaceTileCache(Path root) {
        this.root = root;
    }

    Map<SurfaceTile.Key, SurfaceTile.Version> index(ResourceLocation dimension) throws IOException {
        Path directory = directory(dimension);
        Map<SurfaceTile.Key, SurfaceTile.Version> index = new HashMap<>();
        if (!Files.isDirectory(directory)) {
            return index;
        }
        try (var files = Files.newDirectoryStream(directory, "*.amt")) {
            for (Path file : files) {
                String[] name = file.getFileName().toString().split("\\.");
                if (name.length == 3) {
                    try (var input = Files.newInputStream(file)) {
                        var key = new SurfaceTile.Key(dimension, Integer.parseInt(name[0]), Integer.parseInt(name[1]));
                        index.put(key, SurfaceTile.readVersion(input));
                    } catch (IOException | NumberFormatException ignored) {
                        // A partial or invalid cache entry can be fetched again.
                    }
                }
            }
        }
        return index;
    }

    SurfaceTile read(SurfaceTile.Key key) throws IOException {
        try (var input = Files.newInputStream(path(key))) {
            return SurfaceTile.read(input);
        }
    }

    SurfaceTile store(SurfaceTile.Key key, byte[] encoded) throws IOException {
        SurfaceTile tile = SurfaceTile.read(new ByteArrayInputStream(encoded));
        Path file = path(key);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, encoded);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return tile;
    }

    private Path path(SurfaceTile.Key key) {
        return directory(key.dimension()).resolve(key.x() + "." + key.z() + ".amt");
    }

    private Path directory(ResourceLocation dimension) {
        return root.resolve(dimension.getNamespace() + '~' + dimension.getPath().replace('/', '~'));
    }
}
