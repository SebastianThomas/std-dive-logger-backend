package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.User;

import org.hibernate.exception.DataException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserDataService {
    private final UserRepository userRepository;

    public UserDataService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findUserById(final long userId) {
        return userRepository.findById(userId).map(UserEntity::toRecord).orElseThrow();
    }

    public User saveUser(final String email, final String password) {
        try {
            return userRepository.save(new UserEntity(email, password)).toRecord();
        } catch (final DataException e) {
            if (e.getCause() instanceof final DuplicateKeyException _) {
                throw new IllegalArgumentException("User with email " + email + " already exists");
            }
            throw e;
        }
    }
}
