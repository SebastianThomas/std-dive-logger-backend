package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.photo.DivePhoto;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "t_dive_photo")
@SuppressWarnings("NullAway.Init")
public class DivePhotoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_photo_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_dive_id", nullable = false)
    private DiveEntity dive;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "byte_size")
    private @Nullable Long byteSize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_uploaded_by_user_id", nullable = false)
    private UserEntity uploadedBy;

    @Column(name = "caption")
    private @Nullable String caption;

    @Column(name = "taken_at")
    private @Nullable Instant takenAt;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public DivePhotoEntity() {}

    public DivePhotoEntity(
            final DiveEntity dive,
            final UserEntity uploadedBy,
            final String storagePath,
            final String contentType) {
        this.dive = dive;
        this.uploadedBy = uploadedBy;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.confirmed = false;
    }

    /** Marks the photo available once the frontend's direct PUT to storage has succeeded. */
    public void confirm(final long byteSize) {
        this.byteSize = byteSize;
        this.confirmed = true;
    }

    public Long getId() {
        return id;
    }

    public Long getDiveId() {
        return dive.getId();
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public DivePhoto toRecord() {
        return new DivePhoto(
                id,
                dive.getId(),
                contentType,
                byteSize == null ? 0 : byteSize,
                uploadedBy.getId(),
                caption,
                takenAt,
                createdAt.toInstant(),
                confirmed);
    }
}
