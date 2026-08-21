package ch.sthomas.stddivelogger.data.service.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import io.minio.http.Method;

import jakarta.validation.constraints.NotNull;

import okhttp3.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
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

/**
 * Shared by every app (ws/import-ws/analytics) rather than each carrying its own near-duplicate
 * copy - this is the one place {@code presignedUploadUrl}/{@code download}/{@code delete} are
 * actually implemented against real R2, so consolidating here means any app that needs them (only
 * {@code ws}'s dive-photo gallery does today) gets them for free without a fourth copy.
 */
@Service
@Primary
@Profile("!local-output")
public class R2StorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(R2StorageService.class);
    private final String bucket;
    private final MinioClient client;
    private final String baseUrl;

    public R2StorageService(
            @Value("${ch.sthomas.stddivelogger.storage.r2.bucket}") @NotNull final String bucket,
            @Value("${ch.sthomas.stddivelogger.storage.r2.account-id}") @NotNull
                    final String accountId,
            @Value("${ch.sthomas.stddivelogger.storage.r2.access-key}") @NotNull
                    final String accessKey,
            @Value("${ch.sthomas.stddivelogger.storage.r2.secret-key}") @NotNull
                    final String secretKey,
            @Value("${ch.sthomas.stddivelogger.storage.r2.base-url}") @NotNull
                    final String baseUrl) {
        if (bucket == null || accountId == null || accessKey == null || secretKey == null) {
            logger.info(
                    "One is invalid: Bucket, Account Id, Access Key, Secret Key: {}, {}, {}, {}",
                    bucket,
                    accountId,
                    accessKey,
                    secretKey);
            throw new IllegalArgumentException(
                    "One of bucket, accountId, accessKey, secretKey is null.");
        }
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
    public void upload(
            final @NotNull String path,
            final @NotNull InputStream output,
            final @NotNull String contentType,
            final int contentLength)
            throws IOException {
        try (output) {
            Objects.requireNonNull(contentType, "contentType must not be null");

            final var fallbackPartSize = 8 * 1024 * 1024;
            final var partSize = contentLength > 0 ? -1 : fallbackPartSize;

            logger.info("Put Object: bucket {} -> {}", bucket, path);
            final var putObjectArgs =
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .contentType(contentType)
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
    public PresignedUpload presignedUploadUrl(
            final String path, final String contentType, final int expirySeconds)
            throws IOException {
        try {
            final var url =
                    client.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.PUT)
                                    .bucket(bucket)
                                    .object(path)
                                    .expiry(expirySeconds)
                                    .build());
            return new PresignedUpload(url);
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException
                | IOException e) {
            throw new IOException(
                    MessageFormat.format(
                            "failed to create presigned upload url for path={0}", path),
                    e);
        }
    }

    @Override
    public InputStream download(final String path) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException
                | IOException e) {
            throw new IOException(MessageFormat.format("failed to read for path={0}", path), e);
        }
    }

    @Override
    public void delete(final String path) throws IOException {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(path).build());
        } catch (final ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException
                | IOException e) {
            throw new IOException(MessageFormat.format("failed to delete for path={0}", path), e);
        }
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }
}
