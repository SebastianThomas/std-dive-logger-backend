package ch.sthomas.stddivelogger.analytics.services.storage;

import ch.sthomas.stddivelogger.data.service.storage.StorageService;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@Primary
@Profile("!local-output")
public class R2StorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(R2StorageService.class);
    private final String baseUrl;

    public R2StorageService(
            @Value("${ch.sthomas.stddivelogger.storage.r2.base-url}") @NotNull
                    final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public void upload(
            final @NotNull String path,
            final @NotNull InputStream output,
            final @NotNull String contentType,
            final int contentLength) {
        throw new NotImplementedException();
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }
}
