package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.jspecify.annotations.Nullable;

public class DiveSiteTimezoneListener {
    public interface Resolver {
        @Nullable String resolveZone(double latitude, double longitude);
    }

    private final Resolver resolver;

    public DiveSiteTimezoneListener(final Resolver resolver) {
        this.resolver = resolver;
    }

    @PostLoad
    @PrePersist
    @PreUpdate
    public void resolve(final DiveSiteEntity site) {
        final var location = site.getLocation();
        site.setZoneId(resolver.resolveZone(location.lat(), location.lon()));
    }
}
