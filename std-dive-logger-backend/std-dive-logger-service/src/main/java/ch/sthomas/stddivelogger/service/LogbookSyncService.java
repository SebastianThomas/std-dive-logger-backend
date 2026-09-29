package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.repository.PushSubscriptionRepository;
import ch.sthomas.stddivelogger.data.service.HomeDataService;
import ch.sthomas.stddivelogger.data.service.LogbookSyncDataService;
import ch.sthomas.stddivelogger.model.dive.home.HomeRecentDive;
import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.LogbookSnapshot;
import ch.sthomas.stddivelogger.model.push.LogbookSyncPush;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pushes a small dashboard snapshot to a diver's devices when their logbook changed, so an
 * installed app shows current numbers offline. Silent where the platform allows it, a quiet
 * "Logbook synced" notification on Apple (see {@link PushPlatform}). Runs from the analytics job
 * queue; detection and debouncing live in {@link LogbookSyncDataService}.
 */
@Service
public class LogbookSyncService {

    private static final Logger logger = LoggerFactory.getLogger(LogbookSyncService.class);
    static final int MAX_DIVERS_PER_RUN = 50;
    // Web Push allows ~4 KB of encrypted record; stay well below it.
    static final int MAX_PAYLOAD_BYTES = 3000;
    static final int MAX_TEXT_CHARS = 60;

    private final LogbookSyncDataService syncData;
    private final HomeDataService homeData;
    private final PushSubscriptionRepository subscriptions;
    private final PushService pushService;
    private final JsonMapper jsonMapper;

    public LogbookSyncService(
            final LogbookSyncDataService syncData,
            final HomeDataService homeData,
            final PushSubscriptionRepository subscriptions,
            final PushService pushService,
            final JsonMapper jsonMapper) {
        this.syncData = syncData;
        this.homeData = homeData;
        this.subscriptions = subscriptions;
        this.pushService = pushService;
        this.jsonMapper = jsonMapper;
    }

    /** One run: seeds newly seen divers, pushes to changed ones. Returns deliveries. */
    public int sendDue() {
        final var due = syncData.findDue(MAX_DIVERS_PER_RUN);
        final var changed = new ArrayList<LogbookSyncDataService.Due>();
        for (final var diver : due) {
            if (diver.known()) {
                changed.add(diver);
            } else {
                syncData.seed(diver.userId(), diver.fingerprint());
            }
        }
        if (changed.isEmpty()) {
            return 0;
        }
        final var targetsByUser =
                subscriptions
                        .findByUserIdInAndLogbookSyncTrue(
                                changed.stream().map(LogbookSyncDataService.Due::userId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(PushSubscriptionEntity::getUserId));
        final var now = Instant.now();
        final Map<Long, Payloads> payloads = new HashMap<>();
        final var targets = new ArrayList<PushSubscriptionEntity>();
        for (final var diver : changed) {
            try {
                payloads.put(diver.userId(), payloads(diver.userId(), now));
                targets.addAll(targetsByUser.getOrDefault(diver.userId(), List.of()));
            } catch (final RuntimeException e) {
                logger.warn("Could not build the sync snapshot for diver {}", diver.userId(), e);
            }
        }
        final int delivered =
                pushService.fanOut(
                        targets,
                        target ->
                                Objects.requireNonNull(payloads.get(target.getUserId()))
                                        .forPlatform(PushPlatform.of(target.getEndpoint())),
                        PushOptions.LOGBOOK_SYNC);
        for (final var diver : changed) {
            if (payloads.containsKey(diver.userId())) {
                syncData.markSynced(diver.userId(), diver.fingerprint());
            }
        }
        logger.info(
                "Logbook sync: {} diver(s) changed, {} of {} device push(es) delivered.",
                payloads.size(),
                delivered,
                targets.size());
        return delivered;
    }

    private Payloads payloads(final long userId, final Instant now) {
        final var snapshot = homeData.syncSnapshot(userId);
        final var body = body(snapshot);
        return new Payloads(
                fit(LogbookSyncPush.of(userId, now, true, body, snapshot)),
                fit(LogbookSyncPush.of(userId, now, false, body, snapshot)));
    }

    private static String body(final LogbookSnapshot snapshot) {
        final var dives = snapshot.diveCount() == 1 ? "1 dive" : snapshot.diveCount() + " dives";
        if (snapshot.recentDives().isEmpty()) {
            return dives;
        }
        final var last = snapshot.recentDives().getFirst();
        final var name = last.siteName() != null ? last.siteName() : last.identifier();
        return name == null ? dives : dives + " · last: " + truncate(name);
    }

    /** Serialises, trimming long names and then the oldest recent dives until it fits. */
    byte[] fit(final LogbookSyncPush push) {
        var dives =
                new ArrayList<>(
                        push.snapshot().recentDives().stream()
                                .map(LogbookSyncService::shortened)
                                .toList());
        while (true) {
            final var candidate =
                    new LogbookSyncPush(
                            push.type(),
                            push.version(),
                            push.userId(),
                            push.generatedAt(),
                            push.showNotification(),
                            push.title(),
                            push.body(),
                            push.url(),
                            push.tag(),
                            push.snapshot().withRecentDives(List.copyOf(dives)));
            final byte[] bytes = jsonMapper.writeValueAsBytes(candidate);
            if (bytes.length <= MAX_PAYLOAD_BYTES || dives.isEmpty()) {
                return bytes;
            }
            dives.removeLast();
        }
    }

    private static HomeRecentDive shortened(final HomeRecentDive dive) {
        return new HomeRecentDive(
                dive.id(),
                dive.number(),
                truncate(dive.identifier()),
                truncate(dive.siteName()),
                dive.start(),
                dive.maxDepth(),
                dive.bottomTime(),
                dive.zoneId());
    }

    private static @Nullable String truncate(final @Nullable String text) {
        if (text == null || text.length() <= MAX_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TEXT_CHARS - 1) + "…";
    }

    private record Payloads(byte[] withNotification, byte[] silent) {
        byte[] forPlatform(final PushPlatform platform) {
            return platform.requiresVisibleNotification() ? withNotification : silent;
        }
    }
}
