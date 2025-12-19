package ch.sthomas.stddivelogger.model.entity.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serializable;

@Embeddable
public class ReaderId implements Serializable {

    @Column(name = "dive_id")
    private Long diveId;

    @Column(name = "pk_user_id")
    private Long userId;

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (!(o instanceof final ReaderId readerId)) return false;

        return new EqualsBuilder()
                .append(diveId, readerId.diveId)
                .append(userId, readerId.userId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(diveId).append(userId).toHashCode();
    }
}
