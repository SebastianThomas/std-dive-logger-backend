package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSummary;
import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_pending_import")
@SuppressWarnings("NullAway.Init")
public class PendingImportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_pending_import_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_diver_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private PendingImportSource source;

    @Column(name = "external_id")
    private @Nullable String externalId;

    @Column(name = "filename")
    private @Nullable String filename;

    @Column(name = "dive_identifier_guess")
    private @Nullable String diveIdentifierGuess;

    @Column(name = "site_name_guess")
    private @Nullable String siteNameGuess;

    @Column(name = "latitude_guess")
    private @Nullable Double latitudeGuess;

    @Column(name = "longitude_guess")
    private @Nullable Double longitudeGuess;

    @Column(name = "computer_serial")
    private @Nullable String computerSerial;

    @Column(name = "start_date")
    private @Nullable Instant startDate;

    @Column(name = "duration_seconds")
    private @Nullable Long durationSeconds;

    @Column(name = "max_depth")
    private @Nullable Double maxDepth;

    @Column(name = "payload", nullable = false)
    private PendingImportPayload payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PendingImportEntity() {}

    public PendingImportEntity(
            final UserEntity user,
            final PendingImportSource source,
            final @Nullable String externalId,
            final @Nullable String filename,
            final @Nullable String diveIdentifierGuess,
            final @Nullable String siteNameGuess,
            final @Nullable Double latitudeGuess,
            final @Nullable Double longitudeGuess,
            final @Nullable String computerSerial,
            final @Nullable Instant startDate,
            final @Nullable Long durationSeconds,
            final @Nullable Double maxDepth,
            final PendingImportPayload payload,
            final Instant createdAt) {
        this.user = user;
        this.source = source;
        this.externalId = externalId;
        this.filename = filename;
        this.diveIdentifierGuess = diveIdentifierGuess;
        this.siteNameGuess = siteNameGuess;
        this.latitudeGuess = latitudeGuess;
        this.longitudeGuess = longitudeGuess;
        this.computerSerial = computerSerial;
        this.startDate = startDate;
        this.durationSeconds = durationSeconds;
        this.maxDepth = maxDepth;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public PendingImportSource getSource() {
        return source;
    }

    public @Nullable String getExternalId() {
        return externalId;
    }

    public @Nullable String getFilename() {
        return filename;
    }

    public @Nullable String getDiveIdentifierGuess() {
        return diveIdentifierGuess;
    }

    public @Nullable String getSiteNameGuess() {
        return siteNameGuess;
    }

    public @Nullable Double getLatitudeGuess() {
        return latitudeGuess;
    }

    public @Nullable Double getLongitudeGuess() {
        return longitudeGuess;
    }

    public @Nullable String getComputerSerial() {
        return computerSerial;
    }

    public @Nullable Instant getStartDate() {
        return startDate;
    }

    public @Nullable Long getDurationSeconds() {
        return durationSeconds;
    }

    public @Nullable Double getMaxDepth() {
        return maxDepth;
    }

    public PendingImportPayload getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PendingImportSummary toSummary() {
        final var diveNumberGuess = payload.diveNumberGuess();
        return new PendingImportSummary(
                id,
                source,
                externalId,
                filename,
                diveIdentifierGuess,
                siteNameGuess,
                latitudeGuess,
                longitudeGuess,
                computerSerial,
                startDate,
                durationSeconds,
                maxDepth,
                createdAt,
                diveNumberGuess != null ? diveNumberGuess.number() : null,
                diveNumberGuess != null && diveNumberGuess.isFractional());
    }
}
