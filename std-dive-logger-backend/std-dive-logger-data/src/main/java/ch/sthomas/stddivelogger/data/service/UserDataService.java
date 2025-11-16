package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.GroupRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.GroupEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.User;

import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserDataService {
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    public UserDataService(final UserRepository userRepository, GroupRepository groupRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    public User findUserById(final long userId) {
        return userRepository.findById(userId).map(UserEntity::toRecord).orElseThrow();
    }

    public User saveUser(final String email, final String password, final String name) {
        try {
            return userRepository.save(new UserEntity(email, password, name)).toRecord();
        } catch (final DataIntegrityViolationException e) {
            if (e.getCause() instanceof final ConstraintViolationException c) {
                if (c.getCause() instanceof final PSQLException p
                        && p.getMessage().contains("t_users_email_key")) {
                    throw new IllegalArgumentException(
                            "User with email " + email + " already exists");
                }
            }
            throw e;
        }
    }

    public void deleteUserByEmail(final String email) {
        userRepository.deleteByEmailEqualsIgnoreCase(email);
    }

    public List<User> findUsersByClosestMatchName(final String query, final int pageSize) {
        return userRepository.findByClosestMatchName(query, Pageable.ofSize(pageSize)).stream()
                .map(UserEntity::toRecord)
                .toList();
    }

    public long countUsers() {
        return userRepository.count();
    }

    public Optional<Group> findGroupById(final long id) {
        return groupRepository.findById(id).map(GroupEntity::toRecord);
    }

    public List<Group> findGroupsByClosestMatchName(final String query, final int pageSize) {
        return groupRepository.findByClosestMatchName(query, Pageable.ofSize(pageSize)).stream()
                .map(GroupEntity::toRecord)
                .toList();
    }
}
