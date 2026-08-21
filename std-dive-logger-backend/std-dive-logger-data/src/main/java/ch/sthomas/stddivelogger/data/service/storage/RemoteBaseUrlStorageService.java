package ch.sthomas.stddivelogger.data.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * The {@code !local-output} profile's {@link StorageService}. Deliberately trivial (no {@code
 * MinioClient}, no required credentials) unlike {@link R2StorageService} - {@code
 * DiveDataService}/{@code AnalyticsDataService} call {@link #baseUrl()} on every dive record they
 * return, so this must construct cleanly in every app (including ones like {@code autocomplete}
 * that were never given real R2 credentials), not just {@code ws}.
 */
@Service
@Primary
@Profile("!local-output")
public class RemoteBaseUrlStorageService implements StorageService {

    private final String baseUrl;

    public RemoteBaseUrlStorageService(
            @Value("${ch.sthomas.stddivelogger.storage.r2.base-url:}") final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }
}
