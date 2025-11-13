package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserDataService userDataService;

    public UserService(final UserDataService userDataService) {
        this.userDataService = userDataService;
    }

    public User getUserById(final long userId) {
        return userDataService.findUserById(userId);
    }
}
