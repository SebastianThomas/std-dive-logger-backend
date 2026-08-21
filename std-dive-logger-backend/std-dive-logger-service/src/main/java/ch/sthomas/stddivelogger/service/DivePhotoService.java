package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DivePhotoDataService;
import ch.sthomas.stddivelogger.data.service.storage.ObjectStorageService;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlBody;
import ch.sthomas.stddivelogger.model.controller.dive.DivePhotoUploadUrlResponse;
import ch.sthomas.stddivelogger.model.dive.photo.DivePhoto;
import ch.sthomas.stddivelogger.model.dive.photo.DivePhotoDownload;
import ch.sthomas.stddivelogger.model.exception.ForbiddenException;
import ch.sthomas.stddivelogger.model.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Backs the dive photo gallery (WS4): presigned-upload-then-confirm to avoid routing photo bytes
 * through this backend, and a proxied, authenticated download since photos are private (no public
 * URL is ever handed to the browser, unlike the existing preview image / user icon / background).
 */
@Service
public class DivePhotoService {

    private static final Logger logger = LoggerFactory.getLogger(DivePhotoService.class);

    /** How long an upload URL stays valid before the client must request a new one. */
    private static final int UPLOAD_URL_EXPIRY_SECONDS = 15 * 60;

    // Import-from-URL limits - this fetches a user-supplied URL server-side, so every one of
    // these is a deliberate bound against a hostile/misbehaving remote server, not just a UX
    // nicety.
    private static final int IMPORT_MAX_REDIRECTS = 5;
    private static final long IMPORT_MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    private static final long IMPORT_MAX_SINGLE_FILE_BYTES = 30L * 1024 * 1024;
    private static final int IMPORT_MAX_ARCHIVE_ENTRIES = 200;
    private static final Duration IMPORT_TIMEOUT = Duration.ofSeconds(30);

    /** How long an unconfirmed upload is kept before {@link #expireOldPendingUploads} purges it. */
    private static final Duration PENDING_UPLOAD_EXPIRY = Duration.ofHours(24);

    private static final Map<String, String> IMAGE_EXTENSION_CONTENT_TYPES =
            Map.ofEntries(
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("png", "image/png"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("heic", "image/heic"),
                    Map.entry("heif", "image/heif"),
                    Map.entry("bmp", "image/bmp"));

    private final DivePhotoDataService divePhotoDataService;
    private final DiveService diveService;
    private final ObjectStorageService storageService;

    public DivePhotoService(
            final DivePhotoDataService divePhotoDataService,
            final DiveService diveService,
            @Lazy final ObjectStorageService storageService) {
        this.divePhotoDataService = divePhotoDataService;
        this.diveService = diveService;
        this.storageService = storageService;
    }

    @Transactional
    public DivePhotoUploadUrlResponse requestUploadUrl(
            final User user, final long diveId, final DivePhotoUploadUrlBody body) {
        if (!diveService.hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var storagePath =
                "dive-photos/%d/%s.%s"
                        .formatted(diveId, UUID.randomUUID(), extractExtension(body.filename()));
        final var photo =
                divePhotoDataService.createPending(
                        diveId, user.id(), storagePath, body.contentType());
        try {
            final var presigned =
                    storageService.presignedUploadUrl(
                            storagePath, body.contentType(), UPLOAD_URL_EXPIRY_SECONDS);
            return new DivePhotoUploadUrlResponse(photo.id(), presigned.url());
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not create an upload URL.", e);
        }
    }

    /**
     * Imports one or more photos from a pasted URL (e.g. a OneDrive/Jottacloud share link) -
     * fetched server-side to avoid the browser CORS restrictions a direct client-side fetch of an
     * arbitrary third-party host would hit. A single image URL becomes one photo; a zip archive is
     * unpacked into one photo per image entry, mirroring the client-side zip-upload flow's
     * behaviour but performed here since the bytes never reach the browser at all. Every fetch is
     * bounded (redirect count, total/per-file size, timeout) and the target host is checked against
     * private/loopback/link-local ranges before every hop, since this is a server-side fetch of a
     * user-supplied URL (SSRF surface) - see {@link #assertPublicHost}.
     */
    @Transactional
    public List<DivePhoto> importFromUrl(final User user, final long diveId, final String url) {
        if (!diveService.hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var fetched = fetchUrlSafely(url);
        final var contentType = fetched.contentType();
        if (contentType.startsWith("image/")) {
            return List.of(
                    storeImportedBytes(
                            diveId,
                            user.id(),
                            fetched.body(),
                            contentType,
                            extractExtension(fetched.finalUri().getPath())));
        }
        if (isArchiveContentType(contentType) || url.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return storeImportedArchive(diveId, user.id(), fetched.body());
        }
        throw new IllegalArgumentException(
                "Unsupported content type for import: "
                        + contentType
                        + " (expected an image or a"
                        + " .zip archive of images)");
    }

    private DivePhoto storeImportedBytes(
            final long diveId,
            final long userId,
            final byte[] bytes,
            final String contentType,
            final String extension) {
        final var storagePath =
                "dive-photos/%d/%s.%s".formatted(diveId, UUID.randomUUID(), extension);
        try {
            storageService.upload(
                    storagePath, new ByteArrayInputStream(bytes), contentType, bytes.length);
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not store the imported photo.", e);
        }
        return divePhotoDataService.createConfirmed(
                diveId, userId, storagePath, contentType, bytes.length);
    }

    private List<DivePhoto> storeImportedArchive(
            final long diveId, final long userId, final byte[] archiveBytes) {
        final var photos = new ArrayList<DivePhoto>();
        try (final var zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || photos.size() >= IMPORT_MAX_ARCHIVE_ENTRIES) {
                    continue;
                }
                final var extension = extractExtension(entry.getName());
                final var contentType = IMAGE_EXTENSION_CONTENT_TYPES.get(extension);
                if (contentType == null) {
                    continue;
                }
                final var entryBytes = readBounded(zip, IMPORT_MAX_SINGLE_FILE_BYTES);
                if (entryBytes.length == 0) {
                    continue;
                }
                photos.add(storeImportedBytes(diveId, userId, entryBytes, contentType, extension));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read the imported archive.", e);
        }
        if (photos.isEmpty()) {
            throw new IllegalArgumentException("The archive contained no supported image files.");
        }
        return photos;
    }

    private record FetchedUrl(byte[] body, String contentType, URI finalUri) {}

    private FetchedUrl fetchUrlSafely(final String url) {
        var current = parseHttpUri(url);
        final var client =
                HttpClient.newBuilder()
                        .connectTimeout(IMPORT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        for (var redirects = 0; ; redirects++) {
            assertPublicHost(current);
            final var request =
                    HttpRequest.newBuilder(current)
                            .timeout(IMPORT_TIMEOUT)
                            .header("User-Agent", "std-dive-logger/1.0")
                            .GET()
                            .build();
            final HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (final IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new UncheckedIOException(
                        "Could not fetch " + current, e instanceof IOException io ? io : null);
            }
            final var status = response.statusCode();
            if (status >= 300 && status < 400) {
                if (redirects >= IMPORT_MAX_REDIRECTS) {
                    throw new IllegalArgumentException("Too many redirects fetching " + url);
                }
                final var requestedUri = current;
                final var location =
                        response.headers()
                                .firstValue("Location")
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Redirect with no Location header from "
                                                                + requestedUri));
                current = current.resolve(location);
                continue;
            }
            if (status != 200) {
                throw new IllegalArgumentException(
                        "Fetching " + url + " failed with HTTP status " + status);
            }
            final var contentType =
                    response.headers()
                            .firstValue("Content-Type")
                            .map(v -> v.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                            .orElse("application/octet-stream");
            final byte[] body;
            try (final var in = response.body()) {
                body = readBounded(in, IMPORT_MAX_TOTAL_BYTES);
            } catch (final IOException e) {
                throw new UncheckedIOException("Could not read response from " + current, e);
            }
            return new FetchedUrl(body, contentType, current);
        }
    }

    private static byte[] readBounded(final InputStream in, final long maxBytes)
            throws IOException {
        final var out = new ByteArrayOutputStream();
        final var buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException(
                        "Response exceeded the " + maxBytes + " byte import limit.");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static URI parseHttpUri(final String url) {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (final java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid URL: " + url, e);
        }
        final var scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are supported: " + url);
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("URL has no host: " + url);
        }
        return uri;
    }

    /**
     * Rejects any URL/redirect target whose host resolves to a private, loopback, link-local, or
     * otherwise non-public address - this is a server-side fetch of a URL supplied by the user, so
     * without this check a malicious "photo URL" could be used to reach internal services/metadata
     * endpoints (SSRF). Checked again on every redirect hop, not just the original URL, since a
     * redirect is exactly how this check would otherwise be bypassed.
     */
    private static void assertPublicHost(final URI uri) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve host: " + uri.getHost(), e);
        }
        for (final var address : addresses) {
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress()) {
                logger.warn(
                        "Rejected import-from-URL fetch to non-public address {} for host {}",
                        address,
                        uri.getHost());
                throw new IllegalArgumentException(
                        "URLs pointing at private/internal addresses are not allowed.");
            }
        }
    }

    private static boolean isArchiveContentType(final String contentType) {
        return contentType.equals("application/zip")
                || contentType.equals("application/x-zip-compressed")
                || contentType.equals("application/octet-stream");
    }

    @Transactional
    public DivePhoto confirm(
            final User user, final long diveId, final long photoId, final long byteSize) {
        if (!diveService.hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return divePhotoDataService.confirm(diveId, photoId, byteSize);
    }

    public List<DivePhoto> list(final User user, final long diveId) {
        if (!diveService.hasReadAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        return divePhotoDataService.listConfirmed(diveId);
    }

    /**
     * The proxy download: uses the exact same {@link DiveService#hasReadAccess} check {@code
     * getDiveById} already uses, then streams straight from storage - the caller (browser) never
     * sees a storage path or a signed URL for it.
     */
    public DivePhotoDownload download(final User user, final long diveId, final long photoId) {
        if (!diveService.hasReadAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var entity = divePhotoDataService.findConfirmedOrThrow(diveId, photoId);
        // Validate the stored content type BEFORE opening the storage stream, not after - doing
        // it after (as the controller originally did, parsing straight off the entity once the
        // stream was already open) would leak the stream/connection on a malformed value, since
        // nothing downstream of a thrown exception ever gets to close it.
        MediaType.parseMediaType(entity.getContentType());
        try {
            return new DivePhotoDownload(
                    storageService.download(entity.getStoragePath()), entity.getContentType());
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not read the photo.", e);
        }
    }

    @Transactional
    public void delete(final User user, final long diveId, final long photoId) {
        if (!diveService.hasWriteAccess(user, diveId)) {
            throw ForbiddenException.forDiveId(user, diveId);
        }
        final var entity = divePhotoDataService.findOrThrow(diveId, photoId);
        // Delete the storage object first: if this throws, the DB row (and thus the user's
        // ability to retry the delete) is left intact rather than the row disappearing while the
        // object silently lingers in storage forever.
        try {
            storageService.delete(entity.getStoragePath());
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not delete the photo from storage.", e);
        }
        divePhotoDataService.delete(entity);
    }

    /**
     * Purges upload-url rows whose direct PUT to storage never completed (or never got confirmed) -
     * called by a scheduled job, not user-triggered. Best-effort on the storage side: a photo whose
     * PUT genuinely never happened has nothing to delete from storage (the object was never
     * written), so a delete failure there is logged and the DB row is dropped anyway rather than
     * leaving an unconfirmed row around forever because of it.
     */
    @Transactional
    public int expireOldPendingUploads() {
        final var threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(PENDING_UPLOAD_EXPIRY);
        final var stale = divePhotoDataService.findPendingOlderThan(threshold);
        for (final var entity : stale) {
            try {
                storageService.delete(entity.getStoragePath());
            } catch (final IOException e) {
                logger.warn(
                        "Could not delete storage object for expired pending photo {} at {}"
                                + " (deleting the row anyway)",
                        entity.getId(),
                        entity.getStoragePath(),
                        e);
            }
        }
        divePhotoDataService.deleteAll(stale);
        return stale.size();
    }

    private static String extractExtension(final String filename) {
        final var idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "bin";
        }
        final var ext =
                filename.substring(idx + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return ext.isBlank() ? "bin" : ext;
    }
}
