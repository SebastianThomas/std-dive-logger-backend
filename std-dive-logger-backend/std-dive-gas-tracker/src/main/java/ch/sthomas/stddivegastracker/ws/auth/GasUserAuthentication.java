package ch.sthomas.stddivegastracker.ws.auth;

import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** Principal = {@link GasUser}, so controllers take {@code @AuthenticationPrincipal GasUser}. */
public final class GasUserAuthentication extends AbstractAuthenticationToken {

    private final GasUser user;
    private final Jwt jwt;

    public GasUserAuthentication(final GasUser user, final Jwt jwt) {
        super(List.of());
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public GasUser getPrincipal() {
        return user;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return String.valueOf(user.userId());
    }
}
