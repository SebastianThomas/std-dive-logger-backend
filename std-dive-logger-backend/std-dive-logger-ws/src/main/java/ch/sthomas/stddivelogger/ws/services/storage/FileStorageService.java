package ch.sthomas.stddivelogger.ws.services.storage;

import ch.sthomas.stddivelogger.data.service.storage.StorageService;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

    @Override
    public String baseUrl() {
        return "file://" + BASE_DIR;
    }
}
