package ch.sthomas.stddivelogger.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushPlatformTest {

    @Test
    void appleEndpointsRequireAVisibleNotification() {
        final var platform = PushPlatform.of("https://web.push.apple.com/QGuQyavXutnMH-abc");
        assertThat(platform).isEqualTo(PushPlatform.APPLE);
        assertThat(platform.requiresVisibleNotification()).isTrue();
    }

    @Test
    void chromiumAndFirefoxEndpointsMayStaySilent() {
        assertThat(PushPlatform.of("https://fcm.googleapis.com/fcm/send/abc"))
                .isEqualTo(PushPlatform.OTHER);
        assertThat(PushPlatform.of("https://updates.push.services.mozilla.com/wpush/v2/abc"))
                .isEqualTo(PushPlatform.OTHER);
        assertThat(PushPlatform.OTHER.requiresVisibleNotification()).isFalse();
    }

    @Test
    void lookalikeHostsAndGarbageAreNotApple() {
        assertThat(PushPlatform.of("https://push.apple.com.evil.example/x"))
                .isEqualTo(PushPlatform.OTHER);
        assertThat(PushPlatform.of("not a uri at all")).isEqualTo(PushPlatform.OTHER);
    }
}
