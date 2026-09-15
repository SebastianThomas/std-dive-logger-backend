package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.entity.converter.JsonNodeToStringConverter;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;

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

/** One dive-level value taken from a file, and the value as taken. */
@Entity
@Table(name = "t_dive_import_file_field")
@SuppressWarnings("NullAway.Init")
public class DiveImportFileFieldEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_import_file_field_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fk_dive_import_file_id", nullable = false, updatable = false)
    private long diveImportFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, updatable = false)
    private ImportedDiveField field;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeToStringConverter.class)
    @Column(name = "imported_value")
    private @Nullable JsonNode importedValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiveImportFileFieldEntity() {}

    public DiveImportFileFieldEntity(
            final long diveImportFileId,
            final ImportedDiveField field,
            final @Nullable JsonNode importedValue) {
        this.diveImportFileId = diveImportFileId;
        this.field = field;
        this.importedValue = importedValue;
    }

    public void setImportedValue(final @Nullable JsonNode importedValue) {
        this.importedValue = importedValue;
    }

    public long getDiveImportFileId() {
        return diveImportFileId;
    }

    public ImportedDiveField getField() {
        return field;
    }

    public @Nullable JsonNode getImportedValue() {
        return importedValue;
    }
}
