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

import java.time.Instant;

/**
 * A dive took dive-level values from this stored file; which ones: {@link
 * DiveImportFileFieldEntity}.
 */
@Entity
@Table(name = "t_dive_import_file")
@SuppressWarnings("NullAway.Init")
public class DiveImportFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_import_file_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_dive_id", nullable = false, updatable = false)
    private long diveId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_import_file_id", nullable = false, updatable = false)
    private ImportFileEntity file;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = ImportLocatorToStringConverter.class)
    @Column(name = "locator", nullable = false, updatable = false)
    private ImportLocator locator;

    @Column(name = "parser_version", nullable = false)
    private int parserVersion;

    @Column(name = "last_processed_at", nullable = false)
    private Instant lastProcessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiveImportFileEntity() {}

    public DiveImportFileEntity(
            final long diveId,
            final ImportFileEntity file,
            final ImportLocator locator,
            final int parserVersion) {
        this.diveId = diveId;
        this.file = file;
        this.locator = locator;
        stampVersion(parserVersion);
    }

    public void stampVersion(final int version) {
        this.parserVersion = version;
        this.lastProcessedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getDiveId() {
        return diveId;
    }

    public ImportFileEntity getFile() {
        return file;
    }

    public ImportLocator getLocator() {
        return locator;
    }
}
