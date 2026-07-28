package ch.sthomas.stddivelogger.model.user;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public record User(
        long id,
        String email,
        String password,
        String name,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt,
        @Nullable String customIconUrl,
        @Nullable String customBackgroundUrl)
        implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public FrontendUser toFrontendModel() {
        return new FrontendUser(id, name, customIconUrl, customBackgroundUrl);
    }
}
