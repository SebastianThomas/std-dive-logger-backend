package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.user.Certification;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "t_certification")
@SuppressWarnings("NullAway.Init")
public class CertificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_certification_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_agency_id", nullable = false)
    private CertificationAgencyEntity agency;

    @Column(name = "level", nullable = false)
    private String level;

    @Column(name = "cert_date", nullable = false)
    private LocalDate certDate;

    @Column(name = "cert_id")
    private @Nullable String certId;

    @Column(name = "instructor_name")
    private @Nullable String instructorName;

    @Column(name = "facility")
    private @Nullable String facility;

    @Column(name = "course_link")
    private @Nullable String courseLink;

    @Column(name = "certification_link")
    private @Nullable String certificationLink;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public CertificationEntity() {}

    public CertificationEntity(
            final UserEntity user,
            final CertificationAgencyEntity agency,
            final CertificationBody body) {
        this.user = user;
        update(agency, body);
    }

    public void update(final CertificationAgencyEntity agency, final CertificationBody body) {
        this.agency = agency;
        this.level = body.level();
        this.certDate = body.certDate();
        this.certId = body.certId();
        this.instructorName = body.instructorName();
        this.facility = body.facility();
        this.courseLink = body.courseLink();
        this.certificationLink = body.certificationLink();
    }

    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public Certification toRecord() {
        return new Certification(
                id,
                user.getId(),
                agency.toRecord(),
                level,
                certDate,
                certId,
                instructorName,
                facility,
                courseLink,
                certificationLink);
    }
}
