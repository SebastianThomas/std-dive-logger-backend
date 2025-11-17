package ch.sthomas.stddivelogger.ws.services.storage;

import ch.sthomas.stddivelogger.data.service.storage.StorageService;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;

import jakarta.validation.constraints.NotNull;

import okhttp3.OkHttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

@Service
@Primary
public class R2StorageService implements StorageService {

    private final String bucket;
    private final MinioClient client;
    private final String baseUrl;

    public R2StorageService(
            @Value("${ch.sthomas.stddivelogger.storage.r2.bucket}") final String bucket,
            @Value("${ch.sthomas.stddivelogger.storage.r2.account-id}") final String accountId,
            @Value("${ch.sthomas.stddivelogger.storage.r2.access-key}") final String accessKey,
            @Value("${ch.sthomas.stddivelogger.storage.r2.secret-key}") final String secretKey,
            @Value("${ch.sthomas.stddivelogger.storage.r2.base-url}") final String baseUrl) {
        this.bucket = bucket;
        this.baseUrl = baseUrl;
        final var timeout = Duration.ofSeconds(15);
        final var url = "https://" + accountId + ".r2.cloudflarestorage.com";
        this.client =
                MinioClient.builder()
                        .endpoint(url)
                        .credentials(accessKey, secretKey)
                        .httpClient(
                                new OkHttpClient()
                                        .newBuilder()
                                        .connectTimeout(timeout)
                                        .readTimeout(timeout)
                                        .writeTimeout(timeout)
                                        .build())
                        .build();
    }

    @Override
    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 1000))
    public final void upload(
            final @NotNull String path,
            final @NotNull InputStream output,
            final @NotNull String contentType,
            final int contentLength)
            throws IOException {
        try (output) {
            Objects.requireNonNull(contentType, "contentType must not be null");

            final var fallbackPartSize = 8 * 1024 * 1024;
            final var partSize = contentLength > 0 ? -1 : fallbackPartSize;

            final var putObjectArgs =
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            // .contentType(contentType)
                            // .headers(Map.of())
                            .stream(output, -1, fallbackPartSize) // contentLength, partSize)
                            .build();
            client.putObject(putObjectArgs);
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException
                | IOException e) {
            throw new IOException(MessageFormat.format("failed to write for path={0}", path), e);
        }
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }
}
