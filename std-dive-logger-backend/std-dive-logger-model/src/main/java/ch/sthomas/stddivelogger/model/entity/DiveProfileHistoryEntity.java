package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.entity.converter.TrimCutsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "t_dive_profile_history")
@SuppressWarnings("NullAway.Init")
public class DiveProfileHistoryEntity {
    @Id
    @Column(name = "fk_dive_profile_id")
    private long diveProfileId;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @MapsId
    @JoinColumn(name = "fk_dive_profile_id")
    private DiveProfileEntity diveProfile;

    @Column(name = "original_start")
    private OffsetDateTime originalStart;

    @Column(name = "original_end")
    private OffsetDateTime originalEnd;

    @Column(name = "original_dive_id")
    private long originalDiveId;

    // Kept window after trims, relative to the first sample deeper than 0.5 m - clock-independent,
    // so re-processing can re-apply it to a profile re-derived from its files.
    @Column(name = "trim_start_offset_ms")
    private @Nullable Long trimStartOffsetMs;

    @Column(name = "trim_end_offset_ms")
    private @Nullable Long trimEndOffsetMs;

    // Sections cut out, relative to the same anchor.
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = TrimCutsToStringConverter.class)
    @Column(name = "trim_cuts_ms")
    private @Nullable List<TrimCut> trimCuts;

    // Every sample came from stored files: re-processing may replace the profile, not only add.
    @Column(name = "import_files_complete", nullable = false)
    private boolean importFilesComplete;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public DiveProfileHistoryEntity() {}

    public DiveProfileHistoryEntity(final DiveProfileEntity diveProfileEntity) {
        this(
                diveProfileEntity,
                diveProfileEntity.getDiveId(),
                diveProfileEntity.getStart().atOffset(ZoneOffset.UTC),
                diveProfileEntity.getEnd().atOffset(ZoneOffset.UTC));
    }

    public DiveProfileHistoryEntity(
            final DiveProfileEntity diveProfile,
            final long diveId,
            final OffsetDateTime originalStart,
            final OffsetDateTime originalEnd) {
        this.diveProfile = diveProfile;
        this.originalDiveId = diveId;
        this.originalStart = originalStart;
        this.originalEnd = originalEnd;
    }

    public Instant getOriginalStart() {
        return originalStart.toInstant();
    }

    public Instant getOriginalEnd() {
        return originalEnd.toInstant();
    }

    /**
     * Re-baselines "original" after a reimport - the freshly re-parsed raw times become the new
     * reset target, so a prior manual alignment's offset still resolves correctly afterward.
     */
    public void updateOriginal(final Instant newOriginalStart, final Instant newOriginalEnd) {
        this.originalStart = newOriginalStart.atOffset(ZoneOffset.UTC);
        this.originalEnd = newOriginalEnd.atOffset(ZoneOffset.UTC);
    }

    /** A cut section, in ms from the trim anchor. */
    public record TrimCut(long startMs, long endMs) {
        public static TrimCut of(final Duration start, final Duration end) {
            return new TrimCut(start.toMillis(), end.toMillis());
        }

        public Duration start() {
            return Duration.ofMillis(startMs);
        }

        public Duration end() {
            return Duration.ofMillis(endMs);
        }
    }

    /**
     * Narrows the recorded kept window and adds the cuts; a null bound leaves that end as it was.
     */
    public void recordTrim(
            final @Nullable Duration startOffset,
            final @Nullable Duration endOffset,
            final List<TrimCut> cuts) {
        if (!cuts.isEmpty()) {
            final var all = new ArrayList<>(getTrimCuts());
            all.addAll(cuts);
            trimCuts = List.copyOf(all);
        }
        if (startOffset != null) {
            final var ms = startOffset.toMillis();
            trimStartOffsetMs = trimStartOffsetMs == null ? ms : Math.max(trimStartOffsetMs, ms);
        }
        if (endOffset != null) {
            final var ms = endOffset.toMillis();
            trimEndOffsetMs = trimEndOffsetMs == null ? ms : Math.min(trimEndOffsetMs, ms);
        }
    }

    public @Nullable Duration getTrimStartOffset() {
        return trimStartOffsetMs == null ? null : Duration.ofMillis(trimStartOffsetMs);
    }

    public @Nullable Duration getTrimEndOffset() {
        return trimEndOffsetMs == null ? null : Duration.ofMillis(trimEndOffsetMs);
    }

    public List<TrimCut> getTrimCuts() {
        return trimCuts == null ? List.of() : trimCuts;
    }

    public boolean isImportFilesComplete() {
        return importFilesComplete;
    }

    public void setImportFilesComplete(final boolean importFilesComplete) {
        this.importFilesComplete = importFilesComplete;
    }
}
