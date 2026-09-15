package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.entity.converter.JsonNodeToStringConverter;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflict;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictKind;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflictStatus;

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

/** A re-processing result that would change real data, waiting for the diver's decision. */
@Entity
@Table(name = "t_import_reprocess_conflict")
@SuppressWarnings("NullAway.Init")
public class ImportReprocessConflictEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_import_reprocess_conflict_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "fk_dive_id", nullable = false, updatable = false)
    private long diveId;

    @Column(name = "fk_dive_profile_id", updatable = false)
    private @Nullable Long profileId;

    @Column(name = "fk_dive_import_file_id", updatable = false)
    private @Nullable Long diveImportFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private ReprocessConflictKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", updatable = false)
    private @Nullable ImportedDiveField field;

    @Column(name = "summary", nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeToStringConverter.class)
    @Column(name = "current_value")
    private @Nullable JsonNode currentValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeToStringConverter.class)
    @Column(name = "proposed_value", nullable = false)
    private JsonNode proposedValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReprocessConflictStatus status;

    @Column(name = "resolved_at")
    private @Nullable Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ImportReprocessConflictEntity() {}

    public ImportReprocessConflictEntity(
            final long userId,
            final long diveId,
            final @Nullable Long profileId,
            final @Nullable Long diveImportFileId,
            final ReprocessConflictKind kind,
            final @Nullable ImportedDiveField field,
            final String summary,
            final @Nullable JsonNode currentValue,
            final JsonNode proposedValue) {
        this.userId = userId;
        this.diveId = diveId;
        this.profileId = profileId;
        this.diveImportFileId = diveImportFileId;
        this.kind = kind;
        this.field = field;
        this.summary = summary;
        this.currentValue = currentValue;
        this.proposedValue = proposedValue;
        this.status = ReprocessConflictStatus.OPEN;
    }

    public void resolve(final ReprocessConflictStatus status) {
        this.status = status;
        this.resolvedAt = Instant.now();
    }

    public ReprocessConflict toRecord(final int diveNumber, final @Nullable String diveIdentifier) {
        final var withValues = kind == ReprocessConflictKind.DIVE_FIELD;
        return new ReprocessConflict(
                id,
                diveId,
                diveNumber,
                diveIdentifier,
                profileId,
                kind,
                field,
                summary,
                withValues ? currentValue : null,
                withValues ? proposedValue : null,
                createdAt);
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getDiveId() {
        return diveId;
    }

    public @Nullable Long getProfileId() {
        return profileId;
    }

    public @Nullable Long getDiveImportFileId() {
        return diveImportFileId;
    }

    public ReprocessConflictKind getKind() {
        return kind;
    }

    public @Nullable ImportedDiveField getField() {
        return field;
    }

    public @Nullable JsonNode getCurrentValue() {
        return currentValue;
    }

    public JsonNode getProposedValue() {
        return proposedValue;
    }

    public ReprocessConflictStatus getStatus() {
        return status;
    }
}
