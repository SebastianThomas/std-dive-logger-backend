package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.entity.converter.ImportLocatorToStringConverter;
import ch.sthomas.stddivelogger.model.importfile.ImportLocator;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** A profile was built (partly) from this stored file. */
@Entity
@Table(name = "t_dive_profile_import_file")
@SuppressWarnings("NullAway.Init")
public class DiveProfileImportFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_profile_import_file_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_dive_profile_id", nullable = false, updatable = false)
    private long profileId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_import_file_id", nullable = false, updatable = false)
    private ImportFileEntity file;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = ImportLocatorToStringConverter.class)
    @Column(name = "locator", nullable = false, updatable = false)
    private ImportLocator locator;

    @Column(name = "parser_version", nullable = false)
    private int parserVersion;

    @Column(name = "raw_active_start", nullable = false)
    private Instant rawActiveStart;

    @Column(name = "processed_site_id")
    private @Nullable Long processedSiteId;

    @Column(name = "last_processed_at", nullable = false)
    private Instant lastProcessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiveProfileImportFileEntity() {}

    public DiveProfileImportFileEntity(
            final long profileId,
            final ImportFileEntity file,
            final ImportLocator locator,
            final int parserVersion,
            final Instant rawActiveStart,
            final @Nullable Long processedSiteId) {
        this.profileId = profileId;
        this.file = file;
        this.locator = locator;
        stamp(parserVersion, rawActiveStart, processedSiteId);
    }

    /** Processed with this importer version, reading its active start here, for this site. */
    public void stamp(
            final int version, final Instant rawActiveStart, final @Nullable Long siteId) {
        this.rawActiveStart = rawActiveStart;
        stampVersion(version, siteId);
    }

    /** As {@link #stamp}, keeping the clock reference - e.g. while a clock change awaits review. */
    public void stampVersion(final int version, final @Nullable Long siteId) {
        this.parserVersion = version;
        this.processedSiteId = siteId;
        this.lastProcessedAt = Instant.now();
    }

    /** Makes the next re-processing run look at this link again. */
    public void requestReprocessing() {
        this.parserVersion = 0;
        this.lastProcessedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getProfileId() {
        return profileId;
    }

    public ImportFileEntity getFile() {
        return file;
    }

    public ImportLocator getLocator() {
        return locator;
    }

    public Instant getRawActiveStart() {
        return rawActiveStart;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
