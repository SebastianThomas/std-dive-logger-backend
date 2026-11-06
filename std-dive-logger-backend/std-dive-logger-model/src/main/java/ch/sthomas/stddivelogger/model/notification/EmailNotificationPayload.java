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
            case CHANGE_EMAIL ->
                    throw new IllegalArgumentException(
                            "An email change goes to the new address: use createChangeEmailPayload.");
        };
    }

    /** The confirmation link, sent to the address the account is switching to. */
    public static EmailNotificationPayload createChangeEmailPayload(
            final User user, final String newEmail, final String urlWithToken) {
        return new EmailNotificationPayload(
                newEmail,
                MessageFormat.format("Confirm your new email for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Hello {0},

                        please click the following link to use this address for your account:
                        {1}

                        Until you do, your account keeps its current email address. Confirming
                        signs you out on every device; log in again with this address.

                        In case you did not request this email, you may ignore it.
                        """,
                        user.name(), urlWithToken));
    }

    /** The heads-up to the address the account just left - nothing to click. */
    public static EmailNotificationPayload createEmailChangedNoticePayload(
            final User user, final String newEmail) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Your {0} email address was changed", APP_NAME),
                MessageFormat.format(
                        """
                        Hello {0},

                        the email address of your account was changed to {1}. This address
                        no longer receives emails for it and can no longer be used to log in.

                        If you did not make this change, please contact us right away.
                        """,
                        user.name(), newEmail));
    }

    private static EmailNotificationPayload createVerifyEmailPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Verify Email for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Hello {0},

                        please click the following link to verify your email:
                        {1}

                        If the link does not work, please copy it and open it in your browser.

                        In case you did not request this email, you may ignore it.
                        """,
                        user.name(), urlWithToken));
    }

    private static EmailNotificationPayload createLoginPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Login request for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Hello {0},

                        please click the following link to login:
                        {1}

                        If the link does not work, please copy it and open it in your browser.

                        In case you did not request this email, you may ignore it.
                        """,
                        user.name(), urlWithToken));
    }

    private static EmailNotificationPayload createChangePasswordPayload(
            final User user, final String urlWithToken) {
        return new EmailNotificationPayload(
                user.email(),
                MessageFormat.format("Login request for {0}", APP_NAME),
                MessageFormat.format(
                        """
                        Hello {0},

                        please click the following link to reset your password:
                        {1}

                        If the link does not work, please copy it and open it in your browser.

                        In case you did not request this email, you may ignore it.
                        """,
                        user.name(), urlWithToken));
    }
}
