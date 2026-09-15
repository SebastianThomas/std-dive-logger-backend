package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.entity.converter.JsonNodeToStringConverter;
import ch.sthomas.stddivelogger.model.importfile.ImportFileInfo;
import ch.sthomas.stddivelogger.model.importfile.ImportFileScope;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** A stored upload of an opted-in account; the bytes live in {@code ImportFileStore}. */
@Entity
@Table(name = "t_import_file")
@SuppressWarnings("NullAway.Init")
public class ImportFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_import_file_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_user_id", nullable = false, updatable = false)
    private long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private PendingImportSource source;

    @Column(name = "original_filename")
    private @Nullable String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "dive_count", nullable = false)
    private int diveCount;

    @Column(name = "profile_count", nullable = false)
    private int profileCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private ImportFileScope scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeToStringConverter.class)
    @Column(name = "metadata")
    private @Nullable JsonNode metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ImportFileEntity() {}

    public ImportFileEntity(
            final long userId,
            final PendingImportSource source,
            final @Nullable String originalFilename,
            final String contentType,
            final long sizeBytes,
            final String sha256,
            final String storagePath,
            final int diveCount,
            final int profileCount,
            final @Nullable JsonNode metadata) {
        this.userId = userId;
        this.source = source;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.diveCount = diveCount;
        this.profileCount = profileCount;
        this.scope = ImportFileScope.of(diveCount, profileCount);
        this.metadata = metadata;
    }

    /**
     * The same bytes were uploaded again: counts may differ (a newer importer), keep the newest.
     */
    public ImportFileEntity reuploaded(
            final @Nullable String filename, final int diveCount, final int profileCount) {
        if (originalFilename == null) {
            this.originalFilename = filename;
        }
        this.diveCount = diveCount;
        this.profileCount = profileCount;
        this.scope = ImportFileScope.of(diveCount, profileCount);
        this.updatedAt = Instant.now();
        return this;
    }

    public ImportFileInfo toInfo() {
        return new ImportFileInfo(
                id,
                source,
                originalFilename,
                contentType,
                sizeBytes,
                diveCount,
                profileCount,
                scope,
                createdAt,
                updatedAt);
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public PendingImportSource getSource() {
        return source;
    }

    public @Nullable String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public int getDiveCount() {
        return diveCount;
    }

    public @Nullable JsonNode getMetadata() {
        return metadata;
    }
}
