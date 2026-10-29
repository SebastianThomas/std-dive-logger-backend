package ch.sthomas.stddivelogger.ws.auth;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Ed25519 access-token keys from config: PEM parsing (real or literal {@code \n} line breaks), the
 * pair check and the {@code kid}. Generate a pair with {@code tools/generate-jwt-keys.sh}.
 */
public final class AccessTokenKeys {
    private static final String ALGORITHM = "Ed25519";

    private AccessTokenKeys() {}

    /** PKCS#8 PEM ({@code -----BEGIN PRIVATE KEY-----}) of an Ed25519 key. */
    public static PrivateKey parsePrivateKey(final String pem) {
        final var der = derOf(pem, "PRIVATE KEY");
        try {
            return keyFactory().generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (final InvalidKeySpecException e) {
            throw new IllegalArgumentException("not an Ed25519 PKCS#8 private key", e);
        }
    }

    /** X.509 PEM ({@code -----BEGIN PUBLIC KEY-----}) of an Ed25519 key. */
    public static PublicKey parsePublicKey(final String pem) {
        final var der = derOf(pem, "PUBLIC KEY");
        try {
            return keyFactory().generatePublic(new X509EncodedKeySpec(der));
        } catch (final InvalidKeySpecException e) {
            throw new IllegalArgumentException("not an Ed25519 X.509 public key", e);
        }
    }

    /** First 16 chars of base64url(SHA-256(X.509 DER)) - the JWS header {@code kid}. */
    public static String keyId(final PublicKey publicKey) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Signs and verifies a probe, so a mismatched pair fails at startup, not on every request. */
    public static boolean isPair(final PrivateKey privateKey, final PublicKey publicKey) {
        final var probe = "std-dive-logger access-token key check".getBytes(UTF_8);
        try {
            final var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(probe);
            final var signature = signer.sign();
            final var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(probe);
            return verifier.verify(signature);
        } catch (final GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] derOf(final String pem, final String type) {
        final var begin = "-----BEGIN " + type + "-----";
        final var end = "-----END " + type + "-----";
        final var text = pem.replace("\\n", "\n").replace("\\r", "").strip();
        if (!text.startsWith(begin) || !text.endsWith(end)) {
            throw new IllegalArgumentException("expected a PEM block " + begin + " ... " + end);
        }
        final var body =
                text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(body);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("PEM body is not valid base64", e);
        }
    }

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance(ALGORITHM);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
