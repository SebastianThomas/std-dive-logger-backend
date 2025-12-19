package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.entity.embedded.ReaderId;

import jakarta.persistence.*;

import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "t_readers")
public class ReaderViewEntity {

    @EmbeddedId private ReaderId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("diveId")
    @JoinColumn(name = "dive_id")
    private DiveEntity dive;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "pk_user_id")
    private UserEntity user;

    public DiveEntity getDive() {
        return dive;
    }
}
