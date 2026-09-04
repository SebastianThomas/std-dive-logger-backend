package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiverReminderDataService;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

/**
 * Thin pass-through to {@link DiverReminderDataService} for {@code ws} (matches {@code
 * HomeService}).
 */
@Service
public class ReminderService {

    private final DiverReminderDataService reminderDataService;

    public ReminderService(final DiverReminderDataService reminderDataService) {
        this.reminderDataService = reminderDataService;
    }

    /**
     * @return true if the reminder existed and belonged to this user.
     */
    public boolean dismiss(final User user, final long reminderId) {
        return reminderDataService.dismiss(user.id(), reminderId);
    }
}
