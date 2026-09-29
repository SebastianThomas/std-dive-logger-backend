package ch.sthomas.stddivelogger.model.push;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The logbook-sync push payload (see {@code public/sw-custom.js}): the service worker stores {@code
 * snapshot} for the account in {@code userId} and shows a notification only when {@code
 * showNotification} (Apple requires one per push). {@code title}/{@code body}/{@code url}/{@code
 * tag} are the {@link WebPushMessage} fields, so an older service worker still renders something
 * sensible.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogbookSyncPush(
        String type,
        int version,
        long userId,
        Instant generatedAt,
        boolean showNotification,
        String title,
        String body,
        String url,
        String tag,
        LogbookSnapshot snapshot) {

    public static final String TYPE = "LOGBOOK_SYNC";
    public static final int VERSION = 1;
    public static final String TAG = "dtl-sync";

    public static LogbookSyncPush of(
            final long userId,
            final Instant generatedAt,
            final boolean showNotification,
            final String body,
            final LogbookSnapshot snapshot) {
        return new LogbookSyncPush(
                TYPE,
                VERSION,
                userId,
                generatedAt,
                showNotification,
                "Logbook synced",
                body,
                "/",
                TAG,
                snapshot);
    }
}
