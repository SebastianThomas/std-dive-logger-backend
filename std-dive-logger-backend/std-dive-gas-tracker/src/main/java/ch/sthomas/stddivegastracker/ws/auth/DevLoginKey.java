package ch.sthomas.stddivegastracker.ws.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * LOCAL DEVELOPMENT ONLY: the ephemeral Ed25519 key pair (new on every start) that signs dev-login
 * tokens and that {@link DiveLoggerJwtDecoder} accepts besides the configured public keys. Same
 * conditions as {@link DevLoginController}; without them no such key exists.
 */
@Profile("dev-login")
@ConditionalOnProperty(
        name = "ch.sthomas.stddivegastracker.auth.dev-login-enabled",
        havingValue = "true")
@Component
public final class DevLoginKey {

    private final KeyPair keyPair;
    private final String keyId;

    public DevLoginKey() throws GeneralSecurityException {
        this.keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        this.keyId = DiveLoggerJwtDecoder.keyId(keyPair.getPublic());
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    String keyId() {
        return keyId;
    }
}
