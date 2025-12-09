package ch.sthomas.stddivelogger.model.notification;

import ch.sthomas.stddivelogger.model.user.User;

import java.text.MessageFormat;

public record EmailNotificationPayload(String receiver, String subject, String body)
        implements NotificationPayload<String> {
    private static final String APP_NAME = "std-dive-logger";

    public static EmailNotificationPayload createEmailPayload(
            final User user, final AccountRequestType requestType, final String urlWithToken) {
        return switch (requestType) {
            case VERIFY_EMAIL -> createVerifyEmailPayload(user, urlWithToken);
            case LOGIN -> createLoginPayload(user, urlWithToken);
            case CHANGE_PASSWORD -> createChangePasswordPayload(user, urlWithToken);
        };
    }

    private static EmailNotificationPayload createVerifyEmailPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Verify Email for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Dear {0},

                        please click the following link to verify your email:
                        {1}

                        In case you did not request this email, you may ignore it.
                        """,
                        user.getUsername(), urlWithToken));
    }

    private static EmailNotificationPayload createLoginPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Login request for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Dear {0},

                        please click the following link to login:
                        {1}

                        In case you did not request this email, you may ignore it.
                        """,
                        user.getUsername(), urlWithToken));
    }

    private static EmailNotificationPayload createChangePasswordPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Login request for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Dear {0},

                        please click the following link to reset your password:
                        {1}

                        In case you did not request this email, you may ignore it.
                        """,
                        user.getUsername(), urlWithToken));
    }
}
