package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveLength;

import jakarta.persistence.*;

import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Immutable
public class DiveLengthEntity {
    @Id
    @Column(name = "pk_dive_id")
    private Long id;

    @Column(name = "dive_start", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "dive_end", nullable = false)
    private OffsetDateTime endTime;

    public DiveLength toRecord() {
        return new DiveLength(startTime.toInstant(), endTime.toInstant());
    }
}
