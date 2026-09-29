package ch.sthomas.stddivelogger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.data.service.HomeDataService;
import ch.sthomas.stddivelogger.data.service.LogbookSyncDataService;
import ch.sthomas.stddivelogger.model.dive.home.HomeActivity;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecentDive;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.LogbookSnapshot;
import ch.sthomas.stddivelogger.model.push.LogbookSyncPush;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

class LogbookSyncServiceTest {

    private static final JsonMapper JSON = ObjectMapperUtils.objectMapperBuilder(_ -> {}).build();

    private final LogbookSyncDataService syncData = mock(LogbookSyncDataService.class);
    private final HomeDataService homeData = mock(HomeDataService.class);
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final PushService pushService = mock(PushService.class);
    private final LogbookSyncService service =
            new LogbookSyncService(syncData, homeData, subscriptions, pushService, JSON);

    private static HomeRecentDive dive(final int number, final String site) {
        return new HomeRecentDive(
                number,
                number,
                "Dive " + number,
                site,
                Instant.parse("2026-09-20T09:00:00Z"),
                24.5,
                Duration.ofMinutes(48),
                "Europe/Zurich");
    }

    private static LogbookSnapshot snapshot(final List<HomeRecentDive> recent) {
        return new LogbookSnapshot(
                214,
                214,
                Duration.ofHours(160),
                41.2,
                Instant.parse("2015-05-01T09:00:00Z"),
                Instant.parse("2026-09-20T09:00:00Z"),
                31,
                HomeActivity.EMPTY,
                recent);
    }

    @Test
    void aNewlySeenDiverIsSeededSilently() {
        when(syncData.findDue(anyInt()))
                .thenReturn(List.of(new LogbookSyncDataService.Due(5L, "fp", false)));

        assertThat(service.sendDue()).isZero();

        verify(syncData).seed(5L, "fp");
        verify(pushService, never()).fanOut(anyList(), any(), any());
        verify(syncData, never()).markSynced(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void appleDevicesGetAVisibleNotificationOthersASilentPush() throws Exception {
        when(syncData.findDue(anyInt()))
                .thenReturn(List.of(new LogbookSyncDataService.Due(5L, "fp-2", true)));
        when(homeData.syncSnapshot(5L)).thenReturn(snapshot(List.of(dive(214, "Blue Hole"))));
        final var apple =
                new PushSubscriptionEntity(5L, "https://web.push.apple.com/abc", "p", "a", null);
        final var chrome =
                new PushSubscriptionEntity(5L, "https://fcm.googleapis.com/fcm/x", "p", "a", null);
        when(subscriptions.findByUserIdInAndLogbookSyncTrue(List.of(5L)))
                .thenReturn(List.of(apple, chrome));
        final ArgumentCaptor<Function<PushSubscriptionEntity, byte[]>> payload =
                ArgumentCaptor.forClass(Function.class);
        when(pushService.fanOut(eq(List.of(apple, chrome)), payload.capture(), any()))
                .thenReturn(2);

        assertThat(service.sendDue()).isEqualTo(2);

        final var toApple = JSON.readValue(payload.getValue().apply(apple), LogbookSyncPush.class);
        final var toChrome =
                JSON.readValue(payload.getValue().apply(chrome), LogbookSyncPush.class);
        assertThat(toApple.showNotification()).isTrue();
        assertThat(toChrome.showNotification()).isFalse();
        assertThat(toApple.type()).isEqualTo("LOGBOOK_SYNC");
        assertThat(toApple.userId()).isEqualTo(5L);
        assertThat(toApple.body()).isEqualTo("214 dives · last: Blue Hole");
        assertThat(toApple.snapshot().recentDives()).hasSize(1);
        verify(syncData).markSynced(5L, "fp-2");
    }

    @Test
    void anOversizedSnapshotIsTrimmedBelowThePushLimit() {
        final var longName = "Wreck ".repeat(200);
        final var recent =
                IntStream.range(0, 5).mapToObj(i -> dive(200 + i, longName + i)).toList();
        final var push = LogbookSyncPush.of(5L, Instant.now(), true, "214 dives", snapshot(recent));

        final byte[] bytes = service.fit(push);

        assertThat(bytes.length).isLessThanOrEqualTo(LogbookSyncService.MAX_PAYLOAD_BYTES);
        final var decoded = JSON.readValue(bytes, LogbookSyncPush.class);
        assertThat(decoded.snapshot().recentDives()).isNotEmpty();
        assertThat(decoded.snapshot().recentDives().getFirst().siteName())
                .hasSizeLessThanOrEqualTo(LogbookSyncService.MAX_TEXT_CHARS);
    }

    @Test
    void wireFormatMatchesTheHomeDashboardApi() {
        // sw-custom.js compares generatedAt numerically; the frontend reads the snapshot like
        // GET /v1/home: instants and durations as epoch / plain milliseconds.
        final var generatedAt = Instant.parse("2026-09-28T07:00:00Z");
        final var push =
                LogbookSyncPush.of(
                        5L, generatedAt, false, "214 dives", snapshot(List.of(dive(214, "X"))));

        final var tree = JSON.readTree(service.fit(push));

        assertThat(tree.get("type").asString()).isEqualTo("LOGBOOK_SYNC");
        assertThat(tree.get("generatedAt").asLong()).isEqualTo(generatedAt.toEpochMilli());
        assertThat(tree.get("showNotification").asBoolean()).isFalse();
        final var snapshot = tree.get("snapshot");
        assertThat(snapshot.get("totalBottomTime").asLong())
                .isEqualTo(Duration.ofHours(160).toMillis());
        assertThat(snapshot.get("lastDiveStart").asLong())
                .isEqualTo(Instant.parse("2026-09-20T09:00:00Z").toEpochMilli());
        assertThat(snapshot.get("recentDives").get(0).get("bottomTime").asLong())
                .isEqualTo(Duration.ofMinutes(48).toMillis());
    }
}
