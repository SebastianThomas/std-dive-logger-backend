package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.user.CertificationAgency;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_certification_agency")
@SuppressWarnings("NullAway.Init")
public class CertificationAgencyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_agency_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "full_name")
    private @Nullable String fullName;

    @Column(name = "website_url")
    private @Nullable String websiteUrl;

    @Column(name = "description")
    private @Nullable String description;

    public CertificationAgencyEntity() {}

    /** System-seeded agencies (name only) - see the migration's INSERT statements. */
    public CertificationAgencyEntity(final String name) {
        this.name = name;
    }

    public CertificationAgencyEntity(
            final String name,
            @Nullable final String fullName,
            @Nullable final String websiteUrl,
            @Nullable final String description) {
        this.name = name;
        this.fullName = fullName;
        this.websiteUrl = websiteUrl;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CertificationAgency toRecord() {
        return new CertificationAgency(id, name, fullName, websiteUrl, description);
    }
}
