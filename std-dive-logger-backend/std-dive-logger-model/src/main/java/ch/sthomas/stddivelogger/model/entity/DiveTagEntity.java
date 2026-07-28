package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "t_dive_tags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fk_dive_id", "fk_tag_id"}))
@SuppressWarnings("NullAway.Init")
public class DiveTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_tag_id", nullable = false)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_dive_id", nullable = false)
    private DiveEntity dive;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_tag_id", nullable = false)
    private TagDefinitionEntity tag;

    /** Whether this tag was set manually by the user (true) or computed automatically (false). */
    @Column(name = "manual", nullable = false)
    private boolean manual;

    /**
     * Whether the user explicitly dismissed this auto-detected tag. A dismissed tag is kept in the
     * database so it is not re-added by auto-detection, but it is not surfaced in {@code
     * getTags()}. The flag is cleared when the user manually re-adds the tag.
     */
    @Column(name = "dismissed", nullable = false)
    private boolean dismissed;

    public DiveTagEntity() {}

    public DiveTagEntity(
            final DiveEntity dive, final TagDefinitionEntity tag, final boolean manual) {
        this(dive, tag, manual, false);
    }

    public DiveTagEntity(
            final DiveEntity dive,
            final TagDefinitionEntity tag,
            final boolean manual,
            final boolean dismissed) {
        this.dive = dive;
        this.tag = tag;
        this.manual = manual;
        this.dismissed = dismissed;
    }

    public TagDefinitionEntity getTag() {
        return tag;
    }

    public boolean isManual() {
        return manual;
    }

    public boolean isDismissed() {
        return dismissed;
    }
}
