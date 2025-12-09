package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;
import ch.sthomas.stddivelogger.model.notification.EmailNotificationPayload;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.GroupRole;
import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;

@Service
public class UserDataService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final URI frontendBaseUrl;
    private final EmailRepository emailRepository;
    private final AccountRequestRepository accountRequestRepository;
    private final GroupMemberRepository groupMemberRepository;

    public UserDataService(
            final UserRepository userRepository,
            final GroupRepository groupRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            @Value("${ch.sthomas.stddivelogger.frontend.base-url}") final String frontendBaseUrl,
            final EmailRepository emailRepository,
            final AccountRequestRepository accountRequestRepository,
            GroupMemberRepository groupMemberRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.frontendBaseUrl = URI.create(frontendBaseUrl);
        this.emailRepository = emailRepository;
        this.accountRequestRepository = accountRequestRepository;
        this.groupMemberRepository = groupMemberRepository;
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

    public GroupWithMembers saveGroup(final String name, final User initialAdmin) {
        final var admin = userRepository.findById(initialAdmin.id()).orElseThrow();
        return groupRepository.save(new GroupEntity(name, admin)).toRecordWithMembers();
    }

    public GroupWithMembers joinGroup(final long groupId, final long userId) {
        try {
            groupRepository.joinGroup(groupId, userId, GroupRole.REQUESTED);
            return groupRepository
                    .findById(groupId)
                    .map(GroupEntity::toRecordWithMembers)
                    .orElseThrow();
        } catch (final DataException e) {
            logger.error("Error while joining group", e);
            throw new IllegalArgumentException("Group or user not found.");
        }
    }

    public GroupWithMembers changeRole(
            final long groupId, final long userId, final GroupRole role) {
        groupMemberRepository.save(
                groupMemberRepository
                        .findByGroup_IdAndUser_Id(groupId, userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User was not member of this group, cannot change role."))
                        .setRole(role));
        return groupRepository
                .findById(groupId)
                .map(GroupEntity::toRecordWithMembers)
                .orElseThrow();
    }

    @Transactional
    public boolean createVerifyEmailRequest(final User user) {
        final var type = AccountRequestType.VERIFY_EMAIL;
        final var requestId = UUID.randomUUID();
        final var callback = URI.create(frontendBaseUrl + "/verify-email?token=" + requestId);
        final var verifyEmailPayload =
                EmailNotificationPayload.createEmailPayload(user, type, callback.toString());
        final var email =
                emailRepository.save(
                        new EmailEntity(
                                verifyEmailPayload.receiver(),
                                verifyEmailPayload.subject(),
                                verifyEmailPayload.body()));
        accountRequestRepository.save(
                new AccountRequestEntity(
                        userRepository.findById(user.id()).orElseThrow(), email, type));
        return true;
    }

    public User setVerified(final User user) {
        userRepository.setVerified(user.id());
        return userRepository.findById(user.id()).map(UserEntity::toRecord).orElseThrow();
    }

    public boolean isGroupAdmin(final long groupId, final User admin) {
        return groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, admin.id())
                .map(GroupMemberEntity::getRole)
                .map(GroupRole.ADMIN::equals)
                .orElse(false);
    }
}
