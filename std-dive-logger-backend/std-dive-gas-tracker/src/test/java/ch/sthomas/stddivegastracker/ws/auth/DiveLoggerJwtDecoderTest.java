package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static java.nio.charset.StandardCharsets.UTF_8;

import ch.sthomas.stddivegastracker.support.TestJwts;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jwt.BadJwtException;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * The verification rules of API.md "Auth" without Spring: key selection by {@code kid}, what is
 * required of the claims, and that every failure is a {@link BadJwtException} (401).
 */
final class DiveLoggerJwtDecoderTest {

    private static final String EMAIL = "diver@example.test";
    private static final long UID = 4_000_000_007L;
    private static final KeyPair OTHER = ed25519();
    private static final KeyPair DEV = ed25519();
    private static final String OTHER_KID = TestJwts.keyId(OTHER.getPublic());
    private static final String DEV_KID = TestJwts.keyId(DEV.getPublic());

    private static final DiveLoggerJwtDecoder SINGLE_KEY =
            decoder(List.of(TestJwts.PUBLIC_KEY_PEM));

    private static KeyPair ed25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DiveLoggerJwtDecoder decoder(final List<String> pems) {
        return new DiveLoggerJwtDecoder(pems, TestJwts.ISSUER, true, null);
    }

    private static String pem(final PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static String signedBy(final JwtBuilder builder, final PrivateKey key) {
        return builder.signWith(key, Jwts.SIG.EdDSA).compact();
    }

    private static JwtBuilder validBuilder() {
        return TestJwts.builder(UID, EMAIL);
    }

    // --- the contract of the key id -----------------------------------------------------------

    @Test
    void keyIdIsTheFirst16CharsOfTheBase64UrlSha256OfTheX509Der() {
        // openssl pkey -pubin -in test-only-ed25519-public.pem -outform DER | openssl dgst -sha256
        // -binary | basenc --base64url | tr -d = | cut -c1-16
        assertThat(DiveLoggerJwtDecoder.keyId(TestJwts.PUBLIC_KEY)).isEqualTo("cB9GMHBFTVI4jHqZ");
        assertThat(TestJwts.KEY_ID).isEqualTo("cB9GMHBFTVI4jHqZ");
    }

    // --- accepted ------------------------------------------------------------------------------

    @Test
    void aValidTokenYieldsSubjectUidAndVerifiedFlag() {
        final var jwt = SINGLE_KEY.decode(TestJwts.accessToken(UID, EMAIL));

        assertThat(jwt.getSubject()).isEqualTo(EMAIL);
        assertThat(jwt.<Long>getClaim("uid")).isEqualTo(UID);
        assertThat(jwt.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getHeaders()).containsEntry("kid", TestJwts.KEY_ID);
    }

    @Test
    void aTokenWithoutKidIsAcceptedWhenExactlyOneKeyIsConfigured() {
        final var token = TestJwts.sign(TestJwts.builderWithoutKeyId(UID, EMAIL));

        assertThat(SINGLE_KEY.decode(token).<Long>getClaim("uid")).isEqualTo(UID);
    }

    @Test
    void theSameKeyConfiguredTwiceIsStillOneKey() {
        final var twice = decoder(List.of(TestJwts.PUBLIC_KEY_PEM, TestJwts.PUBLIC_KEY_PEM));

        twice.decode(TestJwts.sign(TestJwts.builderWithoutKeyId(UID, EMAIL)));
    }

    @Test
    void pemsWithLiteralBackslashNewlinesAreAccepted() {
        final var literal = TestJwts.PUBLIC_KEY_PEM.strip().replace("\n", "\\n");

        decoder(List.of(literal)).decode(TestJwts.accessToken(UID, EMAIL));
    }

    @Test
    void anUnverifiedTokenIsAcceptedOnlyWhenTheClaimIsNotRequired() {
        final var token = TestJwts.sign(validBuilder().claim("email_verified", false));
        final var lenient =
                new DiveLoggerJwtDecoder(
                        List.of(TestJwts.PUBLIC_KEY_PEM), TestJwts.ISSUER, false, null);

        assertThat(lenient.decode(token).getClaimAsBoolean("email_verified")).isFalse();
        assertThatThrownBy(() -> SINGLE_KEY.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void severalKeysAreToldApartByKid() {
        final var rotation = decoder(List.of(TestJwts.PUBLIC_KEY_PEM, pem(OTHER.getPublic())));
        final var signedByOther =
                signedBy(validBuilder().header().keyId(OTHER_KID).and(), OTHER.getPrivate());

        assertThat(rotation.decode(TestJwts.accessToken(UID, EMAIL)).getSubject()).isEqualTo(EMAIL);
        assertThat(rotation.decode(signedByOther).getSubject()).isEqualTo(EMAIL);
    }

    @Test
    void withSeveralKeysATokenNeedsAKnownKid() {
        final var rotation = decoder(List.of(TestJwts.PUBLIC_KEY_PEM, pem(OTHER.getPublic())));
        final var noKid = TestJwts.sign(TestJwts.builderWithoutKeyId(UID, EMAIL));
        final var unknownKid =
                TestJwts.sign(validBuilder().header().keyId("0123456789abcdef").and());
        final var wrongKid =
                signedBy(validBuilder().header().keyId(OTHER_KID).and(), TestJwts.PRIVATE_KEY);

        for (final var token : List.of(noKid, unknownKid, wrongKid)) {
            assertThatThrownBy(() -> rotation.decode(token)).isInstanceOf(BadJwtException.class);
        }
    }

    // --- dev-login key -------------------------------------------------------------------------

    @Test
    void theDevKeyIsAcceptedAlongsideAndOnlyItMayCarryANegativeUid() {
        final var withDev =
                new DiveLoggerJwtDecoder(
                        List.of(TestJwts.PUBLIC_KEY_PEM), TestJwts.ISSUER, true, DEV.getPublic());
        final var accepted =
                List.of(
                        signedBy(
                                TestJwts.builder(-42L, EMAIL).header().keyId(DEV_KID).and(),
                                DEV.getPrivate()),
                        TestJwts.accessToken(UID, EMAIL));
        assertThat(accepted.stream().map(token -> withDev.decode(token).<Long>getClaim("uid")))
                .containsExactly(-42L, UID);

        final var rejected =
                List.of(
                        // real key, negative uid
                        TestJwts.accessToken(-42L, EMAIL),
                        // dev key, positive uid
                        signedBy(
                                TestJwts.builder(UID, EMAIL).header().keyId(DEV_KID).and(),
                                DEV.getPrivate()),
                        // kid-less tokens are ambiguous once a second key exists
                        TestJwts.sign(TestJwts.builderWithoutKeyId(UID, EMAIL)));
        for (final var token : rejected) {
            assertThatThrownBy(() -> withDev.decode(token)).isInstanceOf(BadJwtException.class);
        }
    }

    @Test
    void theDevKeyAloneIsEnoughToStart() {
        final var devOnly =
                new DiveLoggerJwtDecoder(List.of(), TestJwts.ISSUER, true, DEV.getPublic());

        // The real test key is not configured here.
        assertThatThrownBy(() -> devOnly.decode(TestJwts.accessToken(UID, EMAIL)))
                .isInstanceOf(BadJwtException.class);
    }

    // --- startup failures ----------------------------------------------------------------------

    @Test
    void withoutAnyKeyStartupFailsClearly() {
        assertThatThrownBy(() -> decoder(List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No JWT verification key")
                .hasMessageContaining("auth.public-keys")
                .hasMessageContaining("dev-login");
    }

    @Test
    void anUnparsableOrWrongKindOfKeyFailsStartupNamingTheEntry() {
        final var notAKey = "-----BEGIN PUBLIC KEY-----\nbm90IGEga2V5\n-----END PUBLIC KEY-----";
        final var notBase64 = "this is not a PEM";

        for (final var bad : List.of(notAKey, notBase64, "", pem(rsa().getPublic()))) {
            assertThatThrownBy(() -> decoder(List.of(TestJwts.PUBLIC_KEY_PEM, bad)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("auth.public-keys[1]")
                    .hasMessageContaining("Ed25519 public key");
        }
    }

    @Test
    void aPrivateKeyIsNotAcceptedAsAPublicKey() {
        final var privatePem =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getEncoder().encodeToString(TestJwts.PRIVATE_KEY.getEncoded())
                        + "\n-----END PUBLIC KEY-----";

        assertThatThrownBy(() -> decoder(List.of(privatePem)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.public-keys[0]");
    }

    // --- rejected: every failure is a BadJwtException ------------------------------------------

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void everyInvalidTokenIsABadJwt(final String token) {
        assertThatThrownBy(() -> SINGLE_KEY.decode(token))
                .isExactlyInstanceOf(BadJwtException.class);
    }

    static Stream<Arguments> invalidTokens() {
        final var now = Instant.now();
        final var parts = TestJwts.accessToken(UID, EMAIL).split("\\.", -1);
        final var otherPayload = TestJwts.accessToken(UID + 1, EMAIL).split("\\.", -1)[1];
        final var unrelatedSecret =
                Keys.hmacShaKeyFor("0123456789abcdef0123456789abcdef".getBytes(UTF_8));
        final var publicKeyAsSecret = Keys.hmacShaKeyFor(TestJwts.PUBLIC_KEY.getEncoded());
        final var rsa = rsa();
        return Stream.of(
                invalid(
                        "HS256 with an unrelated secret",
                        validBuilder().signWith(unrelatedSecret, Jwts.SIG.HS256).compact()),
                invalid(
                        "HS256 keyed with the PUBLIC key bytes (algorithm confusion)",
                        validBuilder().signWith(publicKeyAsSecret, Jwts.SIG.HS256).compact()),
                invalid(
                        "RS256",
                        validBuilder().signWith(rsa.getPrivate(), Jwts.SIG.RS256).compact()),
                invalid("alg none", validBuilder().compact()),
                invalid(
                        "another Ed25519 key behind our kid",
                        signedBy(validBuilder(), OTHER.getPrivate())),
                invalid("wrong iss", TestJwts.sign(validBuilder().issuer("someone-else"))),
                invalid("missing iss", TestJwts.sign(validBuilder().issuer(null))),
                invalid("missing uid", TestJwts.sign(TestJwts.builder(null, EMAIL))),
                invalid("string uid", TestJwts.sign(validBuilder().claim("uid", "4000000007"))),
                invalid("fractional uid", TestJwts.sign(validBuilder().claim("uid", 1.5))),
                invalid("boolean uid", TestJwts.sign(validBuilder().claim("uid", true))),
                invalid("uid 0", TestJwts.sign(TestJwts.builder(0L, EMAIL))),
                invalid(
                        "negative uid from a non-dev key",
                        TestJwts.sign(TestJwts.builder(-1L, EMAIL))),
                invalid(
                        "email_verified false",
                        TestJwts.sign(validBuilder().claim("email_verified", false))),
                invalid(
                        "email_verified missing",
                        TestJwts.sign(validBuilder().claim("email_verified", null))),
                invalid(
                        "email_verified as a string",
                        TestJwts.sign(validBuilder().claim("email_verified", "true"))),
                invalid(
                        "expired",
                        TestJwts.sign(
                                validBuilder()
                                        .expiration(Date.from(now.minus(Duration.ofMinutes(5)))))),
                invalid("no exp", TestJwts.sign(validBuilder().expiration(null))),
                invalid(
                        "not yet valid",
                        TestJwts.sign(
                                validBuilder()
                                        .notBefore(Date.from(now.plus(Duration.ofMinutes(5)))))),
                invalid(
                        "sub is not an email",
                        TestJwts.sign(validBuilder().subject("not-an-email"))),
                invalid("no sub", TestJwts.sign(validBuilder().subject(null))),
                invalid(
                        "unknown kid",
                        TestJwts.sign(validBuilder().header().keyId("0123456789abcdef").and())),
                invalid(
                        "payload swapped after signing",
                        parts[0] + "." + otherPayload + "." + parts[2]),
                invalid("signature cut off", parts[0] + "." + parts[1] + "."),
                invalid("two parts", parts[0] + "." + parts[1]),
                invalid("garbage", "not-a-jwt"),
                invalid("empty", ""));
    }

    private static KeyPair rsa() {
        try {
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Arguments invalid(final String name, final String token) {
        return Arguments.of(Named.of(name, token));
    }
}
