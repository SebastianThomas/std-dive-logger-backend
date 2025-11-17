package ch.sthomas.stddivelogger.data.service.storage;

import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    void upload(
            @NotNull String path,
            @NotNull InputStream output,
            @NotNull String contentType,
            int contentLength)
            throws IOException;

    String baseUrl();
}
