package ch.sthomas.stddivelogger.data.service.storage;

/**
 * The one storage capability every app needs just to render a {@code Dive}/{@code SimplifiedDive}
 * record (photo/preview URLs are built against this) - kept separate from {@link
 * ObjectStorageService}'s actual upload/download/delete so that apps which never touch object
 * storage (e.g. {@code autocomplete}) aren't forced to construct a real, credential-requiring
 * client just to satisfy this dependency.
 */
public interface StorageService {

    String baseUrl();
}
