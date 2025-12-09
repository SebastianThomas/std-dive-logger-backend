package ch.sthomas.stddivelogger.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public class SecurityUtils {
    private static final MessageDigest TOKEN_DIGEST;

    static {
        try {
            TOKEN_DIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashToken(final String token) {
        return HexFormat.of()
                .formatHex(TOKEN_DIGEST.digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    public static String createToken() {
        return UUID.randomUUID().toString();
    }
}
