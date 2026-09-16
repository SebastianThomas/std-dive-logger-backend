package ch.sthomas.stddivelogger.data.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Stored upload bytes, on a local volume rather than object storage (cheaper for small files kept
 * indefinitely). In Kubernetes every app mounts the same volume here - see {@code
 * deploy/base/import-files-pvc.yaml}. Paths are relative, one directory per account.
 */
@Service
public class ImportFileStore {

    private final Path baseDir;

    public ImportFileStore(
            @Value("${ch.sthomas.stddivelogger.import-files.dir:local-storage/import-files}")
                    final String dir) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Content-addressed paths never change content, so an existing file is left as it is. */
    public void write(final String path, final byte[] bytes) throws IOException {
        final var target = resolve(path);
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        final var temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public byte[] read(final String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }

    public void delete(final String path) throws IOException {
        Files.deleteIfExists(resolve(path));
    }

    public boolean exists(final String path) {
        return Files.exists(resolve(path));
    }

    private Path resolve(final String path) {
        final var resolved = baseDir.resolve(path).normalize();
        if (!resolved.startsWith(baseDir) || resolved.equals(baseDir)) {
            throw new IllegalArgumentException("Invalid import file path " + path);
        }
        return resolved;
    }
}
