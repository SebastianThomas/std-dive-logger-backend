package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveSiteLink;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_site_link")
@SuppressWarnings("NullAway.Init")
public class DiveSiteLinkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_id", nullable = false)
    private Long id;

    @JoinColumn(name = "fk_site_id", nullable = false)
    @ManyToOne
    private DiveSiteEntity diveSite;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "label")
    private @Nullable String label;

    public DiveSiteLinkEntity() {}

    public DiveSiteLinkEntity(
            final DiveSiteEntity diveSite, final String url, @Nullable final String label) {
        this.diveSite = diveSite;
        this.url = url;
        this.label = label;
    }

    public DiveSiteLink toRecord() {
        return new DiveSiteLink(id, url, label);
    }
}
