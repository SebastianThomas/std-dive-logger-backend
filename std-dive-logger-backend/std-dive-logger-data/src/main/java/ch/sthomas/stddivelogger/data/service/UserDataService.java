package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.GroupRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.entity.GroupEntity;
import ch.sthomas.stddivelogger.model.entity.UserEntity;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserDataService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);
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

    public PagedResponse<User> findUsersByClosestMatchName(
            final String query, final Pageable pageable) {
        return PagedResponse.of(
                userRepository.findByClosestMatchName(query, pageable), UserEntity::toRecord);
    }

    public long countUsers() {
        return userRepository.count();
    }

    public Optional<Group> findGroupById(final long id) {
        return groupRepository.findById(id).map(GroupEntity::toRecord);
    }

    public Optional<GroupWithMembers> findGroupWithMembersById(final long id) {
        return groupRepository.findById(id).map(GroupEntity::toRecordWithMembers);
    }

    public List<Group> findGroupsByClosestMatchName(final String query, final Pageable pageable) {
        return groupRepository.findByClosestMatchName(query, pageable).stream()
                .map(GroupEntity::toRecord)
                .toList();
    }

    public GroupWithMembers saveGroup(final String name, final Collection<Long> initialMembers) {
        final var members =
                initialMembers.stream()
                        .map(userRepository::findById)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());
        return groupRepository.save(new GroupEntity(name, members)).toRecordWithMembers();
    }

    public GroupWithMembers joinGroup(final long groupId, final long userId) {
        try {
            groupRepository.joinGroup(groupId, userId);
            return groupRepository
                    .findById(groupId)
                    .map(GroupEntity::toRecordWithMembers)
                    .orElseThrow();
        } catch (final DataException e) {
            logger.error("Error while joining group", e);
            throw new IllegalArgumentException("Group or user not found.");
        }
    }
}
