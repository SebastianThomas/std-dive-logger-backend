package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.AccountRequestRepository;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.exception.InvalidPasswordException;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.exception.UserCreationException;
import ch.sthomas.stddivelogger.model.notification.AccountRequest;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;
import ch.sthomas.stddivelogger.model.user.*;
import ch.sthomas.stddivelogger.utils.SecurityUtils;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.passay.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class UserService {
    public static final int USERS_PAGE_SIZE = 10;
    public static final int GROUPS_PAGE_SIZE = 10;

    private final UserDataService userDataService;
    private final PasswordEncoder passwordEncoder;

    private final Pattern emailPattern = Pattern.compile("(?<name>.*)@(?<domain>.*)");
    private final PasswordValidator passwordValidator =
            new PasswordValidator(
                    new LengthRule(8, 64),
                    new CharacterRule(EnglishCharacterData.UpperCase, 1),
                    new CharacterRule(EnglishCharacterData.LowerCase, 1),
                    new CharacterRule(EnglishCharacterData.Digit, 1),
                    new CharacterRule(EnglishCharacterData.Special, 1),
                    new IllegalSequenceRule(EnglishSequenceData.Alphabetical, 4, false),
                    new IllegalSequenceRule(EnglishSequenceData.Numerical, 4, false),
                    new IllegalSequenceRule(EnglishSequenceData.USQwerty, 4, false),
                    new WhitespaceRule());
    private final AccountRequestRepository accountRequestRepository;

    public UserService(
            final UserDataService userDataService,
            final PasswordEncoder passwordEncoder,
            final AccountRequestRepository accountRequestRepository) {
        this.userDataService = userDataService;
        this.passwordEncoder = passwordEncoder;
        this.accountRequestRepository = accountRequestRepository;
    }

    public User getUserById(final long userId) {
        return userDataService.findUserById(userId);
    }

    @Transactional
    public User createUser(final String emailParam, final String password, final String name) {
        final var email = emailParam.trim();
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("This email address seems to be invalid");
        }
        final var passwordValidated = isValidPassword(password);
        if (!passwordValidated.isValid()) {
            throw new InvalidPasswordException(
                    passwordValidated.getDetails().stream()
                            .map(RuleResultDetail::toString)
                            .toList());
        }
        if (userDataService.findUserByName(name).isPresent()) {
            throw new IllegalArgumentException(
                    "This username is already in use, please choose a unique username.");
        }
        final var user = userDataService.saveUser(email, passwordEncoder.encode(password), name);
        if (userDataService.createAccountRequest(AccountRequestType.VERIFY_EMAIL, user)) {
            return user;
        }
        throw new UserCreationException("Could not create verify Email Request.");
    }

    private boolean isValidEmail(final String email) {
        final var matcher = emailPattern.matcher(email);
        if (!matcher.matches()) {
            return false;
        }
        final var name = matcher.group("name");
        final var domain = matcher.group("domain");
        return !name.isEmpty()
                && !domain.isEmpty()
                && domain.indexOf('.') > 1
                && !domain.substring(domain.lastIndexOf('.')).isBlank();
    }

    private RuleResult isValidPassword(final String password) {
        return passwordValidator.validate(new PasswordData(password));
    }

    public void deleteUser(final User user) {
        userDataService.deleteUserByEmail(user.email());
    }

    public PagedResponse<User> getUsersByPartialName(final String query, final int page) {
        return userDataService.findUsersByClosestMatchName(
                query, PageRequest.of(page, USERS_PAGE_SIZE));
    }

    public long getUserCount() {
        return userDataService.countUsers();
    }

    public PagedResponse<GroupWithRole> getGroups(
            final User user,
            @Nullable final GroupRole role,
            @Nullable final GroupRole exclRole,
            final int page) {
        return userDataService.findGroups(user, role, exclRole, page, GROUPS_PAGE_SIZE);
    }

    public Optional<Group> getGroupById(final long id) {
        return userDataService.findGroupById(id);
    }

    public Optional<GroupWithMembers> getGroupWithMembersById(final User user, final long id) {
        final var groupWithMembers = userDataService.findGroupWithMembersById(id);
        if (groupWithMembers.isEmpty()) {
            return Optional.empty();
        }
        if (!hasMemberAccess(groupWithMembers.get(), user)) {
            throw new UnauthorizedException(
                    "You can only get group members for groups you are a member of.");
        }
        return groupWithMembers;
    }

    public List<Group> getGroupsByPartialName(final String query, final int page) {
        return userDataService.findGroupsByClosestMatchName(
                query, PageRequest.of(page, GROUPS_PAGE_SIZE));
    }

    public GroupWithMembers saveGroup(final String name, final User initialAdmin) {
        return userDataService.saveGroup(name, initialAdmin);
    }

    public GroupWithMembers changeRole(
            final User admin, final long groupId, final long userId, final GroupRole role) {
        if (!userDataService.isGroupAdmin(groupId, admin)) {
            throw new UnauthorizedException("User does not have permission to update this group");
        }
        return userDataService.changeRole(groupId, userId, role);
    }

    private boolean hasMemberAccess(final GroupWithMembers group, final User user) {
        final var userId = user.id();
        if (group.members() == null || group.admins() == null) {
            throw new IllegalArgumentException(
                    "Group with members should have non-null members to check access.");
        }
        return Stream.concat(group.members().stream(), group.admins().stream())
                .anyMatch(member -> member.id() == userId);
    }

    public GroupWithMembers joinGroup(final long groupId, final long userId) {
        return userDataService.joinGroup(groupId, userId);
    }

    @Transactional
    public User setVerified(@NotBlank final String token) {
        final var user =
                userDataService
                        .findAndDeleteAccountRequestEntityById(SecurityUtils.hashToken(token))
                        .filter(r -> r.type() == AccountRequestType.VERIFY_EMAIL)
                        .map(AccountRequest::user)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "There is no open account request for token "
                                                        + token));
        return userDataService.setVerified(user);
    }

    public List<GroupRequest> getAdminGroupRequests(final User user) {
        return userDataService.findAdminGroupRequests(user);
    }

    public void createLoginToken(@Valid @Email final String email) {
        final var user =
                userDataService
                        .findUserByEmail(email)
                        .orElseThrow(() -> new NoSuchElementException("No user with given email."));
        userDataService.createAccountRequest(AccountRequestType.LOGIN, user);
    }
}
