package ch.sthomas.stddivelogger.utils;

public class LogSanitizer {
    private LogSanitizer() {}

    public static String sanitizeEmail(final String email) {
        final var at = email.indexOf(email);
        if (at == -1) {
            return toStars(email);
        }
        return toStars(email.substring(0, at)) + email.substring(at);
    }

    public static String sanitizePassword(final String password) {
        return toStars(password);
    }

    private static String toStars(final String name) {
        return name.chars()
                .mapToObj(_ -> '*')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
