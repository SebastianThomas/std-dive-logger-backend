package ch.sthomas.stddivelogger.model.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public record User(long id, String email, String password, Instant createdAt, Instant updatedAt)
        implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(); // TODO
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
        return new FrontendUser(id, email);
    }
}
