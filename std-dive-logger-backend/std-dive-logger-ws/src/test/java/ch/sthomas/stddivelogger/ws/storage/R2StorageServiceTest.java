package ch.sthomas.stddivelogger.ws.storage;

import ch.sthomas.stddivelogger.ws.services.storage.R2StorageService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class R2StorageServiceTest {
    @Test
    @Disabled("Requires ENV variables")
    public void testR2StorageService() throws IOException {
        final var service =
                new R2StorageService(
                        System.getenv("STD_DIVE_LOGGER_STORAGE_R2_BUCKET"),
                        System.getenv("STD_DIVE_LOGGER_STORAGE_R2_ACCOUNT_ID"),
                        System.getenv("STD_DIVE_LOGGER_STORAGE_R2_ACCESS_KEY"),
                        System.getenv("STD_DIVE_LOGGER_STORAGE_R2_SECRET_KEY"),
                        System.getenv("STD_DIVE_LOGGER_STORAGE_R2_BASE_URL"));
        final var bytes = "Test ABC".getBytes();
        service.upload("test/abc.txt", new ByteArrayInputStream(bytes), "txt", bytes.length);
    }
}
