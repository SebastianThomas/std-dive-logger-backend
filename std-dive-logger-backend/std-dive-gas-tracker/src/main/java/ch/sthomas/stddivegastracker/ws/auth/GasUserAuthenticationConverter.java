package ch.sthomas.stddivegastracker.ws.auth;

import ch.sthomas.stddivegastracker.service.UserService;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Verified JWT -> {@link GasUserAuthentication}: syncs the user (identity = {@code uid}) with the
 * token's email and binds invitations, once per request (DOMAIN §2).
 */
@Component
public final class GasUserAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    public GasUserAuthenticationConverter(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public AbstractAuthenticationToken convert(final Jwt jwt) {
        // DiveLoggerJwtDecoder guarantees an email subject and a numeric uid.
        final var user =
                userService.resolveAuthenticated(
                        Objects.requireNonNull(jwt.<Long>getClaim(DiveLoggerJwtDecoder.UID_CLAIM)),
                        Objects.requireNonNull(jwt.getSubject()),
                        Boolean.TRUE.equals(
                                jwt.getClaimAsBoolean(DiveLoggerJwtDecoder.EMAIL_VERIFIED_CLAIM)));
        return new GasUserAuthentication(user, jwt);
    }
}
