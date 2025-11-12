package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

@Service
public class UserDataService {
    private final UserRepository userRepository;

    public UserDataService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findUserById(final int userId) {
        return userRepository.findById(userId).map(UserEntity::toRecord).orElseThrow();
    }
}
