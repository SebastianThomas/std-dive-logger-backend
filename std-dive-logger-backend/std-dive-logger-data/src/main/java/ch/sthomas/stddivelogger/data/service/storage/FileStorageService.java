package ch.sthomas.stddivelogger.data.service.storage;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared by every app (ws/import-ws/analytics) rather than each carrying its own near-duplicate
 * copy - see {@link R2StorageService}'s own doc for why they live in this module.
 */
@Service
@Primary
@Profile("local-output")
public class FileStorageService implements StorageService {
    private static final Path BASE_DIR =
            Path.of(System.getProperty("user.dir")).resolve("local-storage").toAbsolutePath();

    @Override
    public void upload(
            final String path,
            final InputStream output,
            final String contentType,
            final int contentLength)
            throws IOException {
        final var absPath = BASE_DIR.resolve(path).toAbsolutePath();
        Files.createDirectories(absPath.getParent());
        Files.copy(output, absPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Local disk has no real presigned-URL concept, so this degrades gracefully: it returns a
     * relative URL pointing at a plain authenticated local-upload endpoint (only the {@code ws} app
     * actually serves one, at {@code /v1/storage/local-upload} - the only app that uses presigned
     * uploads today) that accepts a direct PUT and writes it to disk via this same service - same
     * client-side shape (request a URL, then PUT bytes to it directly) as the real presigned-URL
     * flow used in production.
     */
    @Override
    public PresignedUpload presignedUploadUrl(
            final String path, final String contentType, final int expirySeconds) {
        final var encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return new PresignedUpload("/v1/storage/local-upload?path=" + encodedPath);
    }

    @Override
    public InputStream download(final String path) throws IOException {
        final var absPath = BASE_DIR.resolve(path).toAbsolutePath();
        return Files.newInputStream(absPath);
    }

    @Override
    public void delete(final String path) throws IOException {
        final var absPath = BASE_DIR.resolve(path).toAbsolutePath();
        Files.deleteIfExists(absPath);
    }

    @Override
    public String baseUrl() {
        return "file://" + BASE_DIR;
    }
}
