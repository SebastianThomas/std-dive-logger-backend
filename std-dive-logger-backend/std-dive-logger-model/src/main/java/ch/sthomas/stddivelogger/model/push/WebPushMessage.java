package ch.sthomas.stddivelogger.model.push;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * The notification payload the service worker receives in a {@code push} event (serialised to JSON,
 * then VAPID-signed + encrypted by the sender). Keep this in sync with {@code public/sw-custom.js}
 * on the frontend.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebPushMessage(
        String title,
        String body,
        // in-app path to open on click, e.g. "/" or "/dives/view/228"
        String url,
        // collapse key: a second push with the same tag replaces the first in the tray
        @Nullable String tag) {

    public static WebPushMessage of(final String title, final String body, final String url) {
        return new WebPushMessage(title, body, url, null);
    }
}
