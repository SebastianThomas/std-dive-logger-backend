package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.*;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.notification.AccountRequest;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;
import ch.sthomas.stddivelogger.model.notification.EmailNotificationPayload;
import ch.sthomas.stddivelogger.model.user.*;
import ch.sthomas.stddivelogger.utils.SecurityUtils;

import com.google.common.collect.MoreCollectors;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.NotImplementedException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Service
public class UserDataService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataService.class);
    private static final int MAX_CONCURRENT_JOIN_REQUESTS = 5;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    private final URI frontendBaseUrl;
    private final EmailRepository emailRepository;
    private final AccountRequestRepository accountRequestRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public UserDataService(
            final UserRepository userRepository,
            final GroupRepository groupRepository,
            @Value("${ch.sthomas.stddivelogger.frontend.base-url}") final String frontendBaseUrl,
            final EmailRepository emailRepository,
            final AccountRequestRepository accountRequestRepository,
            final GroupMemberRepository groupMemberRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.frontendBaseUrl = URI.create(frontendBaseUrl);
        this.emailRepository = emailRepository;
        this.accountRequestRepository = accountRequestRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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

    public Optional<User> findUserByEmail(final String email) {
        return userRepository.findByEmailIgnoreCase(email).map(UserEntity::toRecord);
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
        if (groupMemberRepository.existsByGroup_IdAndUser_Id(groupId, userId)) {
            throw new IllegalArgumentException(
                    "You already requested to be a member of this group.");
        }
        final var role = GroupRole.REQUESTED;
        final var currentJoinRequestCount =
                groupMemberRepository.countByUser_IdAndRole(userId, role);
        if (currentJoinRequestCount > MAX_CONCURRENT_JOIN_REQUESTS) {
            throw new IllegalArgumentException(
                    "You already have "
                            + MAX_CONCURRENT_JOIN_REQUESTS
                            + " group join requests pending, please check back later.");
        }
        try {
            groupRepository.joinGroup(groupId, userId, role);
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
    public boolean createAccountRequest(final AccountRequestType type, final User user) {
        final var requestId = SecurityUtils.createToken();
        final var callback = URI.create(getCallbackUrl(type, requestId));
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
                        SecurityUtils.hashToken(requestId),
                        userRepository.findById(user.id()).orElseThrow(),
                        email,
                        type,
                        Instant.now().plus(type.getValidDuration())));
        return true;
    }

    private String getCallbackUrl(final AccountRequestType type, final String token) {
        return switch (type) {
            case VERIFY_EMAIL -> frontendBaseUrl + "/user/email?token=" + token;
            case LOGIN -> frontendBaseUrl + "/login?token=" + token;
            case CHANGE_PASSWORD -> throw new NotImplementedException(); // TODO
        };
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

    public List<GroupRequest> findAdminGroupRequests(final User user) {
        return groupMemberRepository
                .findRequests(user.id(), GroupRole.ADMIN, GroupRole.REQUESTED)
                .stream()
                .map(
                        e ->
                                new GroupRequest(
                                        e.getUserEntity().toRecord().toFrontendModel(),
                                        e.getRole()))
                .toList();
    }

    @Transactional
    public Optional<AccountRequest> findAndDeleteAccountRequestEntityById(final String id) {
        return namedParameterJdbcTemplate
                .query(
                        """
                        DELETE FROM t_account_request
                        WHERE pk_account_request_id = :id
                        RETURNING t_account_request.*
                        """,
                        new MapSqlParameterSource(Map.of("id", id)),
                        new BeanPropertyRowMapper<AccountRequestEntity>())
                .stream()
                .collect(MoreCollectors.toOptional())
                .flatMap(AccountRequestEntity::toRecord);
    }

    public PagedResponse<GroupWithRole> findGroups(
            final User user,
            @Nullable final GroupRole role,
            @Nullable final GroupRole exclRole,
            final int page,
            final int groupsPageSize) {
        if (role != null && exclRole != null) {
            throw new IllegalArgumentException("Only one of role, exclRole can be set.");
        }
        final var pageRequest = PageRequest.of(page, groupsPageSize);
        final var result =
                Optional.ofNullable(role)
                        .map(findGroupsWithRole(user, pageRequest))
                        .or(
                                () ->
                                        Optional.ofNullable(exclRole)
                                                .map(findGroupsWithoutRole(user, pageRequest)))
                        .orElseGet(
                                () ->
                                        groupMemberRepository.findByUser_IdOrderById(
                                                user.id(), pageRequest));
        return PagedResponse.of(result, GroupMemberEntity::toRecordWithRole);
    }

    private Function<GroupRole, Page<GroupMemberEntity>> findGroupsWithRole(
            final User user, final Pageable pageable) {
        return r -> groupMemberRepository.findByUser_IdAndRoleOrderById(user.id(), r, pageable);
    }

    private Function<GroupRole, Page<GroupMemberEntity>> findGroupsWithoutRole(
            final User user, final Pageable pageable) {
        return r -> groupMemberRepository.findByUser_IdAndRoleNotOrderById(user.id(), r, pageable);
    }
}
