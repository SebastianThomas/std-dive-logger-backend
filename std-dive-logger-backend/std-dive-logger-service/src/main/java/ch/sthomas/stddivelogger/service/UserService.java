package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.exception.InvalidPasswordException;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.Group;
import ch.sthomas.stddivelogger.model.user.GroupWithMembers;
import ch.sthomas.stddivelogger.model.user.User;

import org.passay.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

    public UserService(
            final UserDataService userDataService, final PasswordEncoder passwordEncoder) {
        this.userDataService = userDataService;
        this.passwordEncoder = passwordEncoder;
    }

    public User getUserById(final long userId) {
        return userDataService.findUserById(userId);
    }

    public User createUser(final String emailParam, final String password, final String name) {
        final var email = emailParam.trim();
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email");
        }
        final var passwordValidated = isValidPassword(password);
        if (!passwordValidated.isValid()) {
            throw new InvalidPasswordException(
                    passwordValidated.getDetails().stream()
                            .map(RuleResultDetail::toString)
                            .toList());
        }
        return userDataService.saveUser(email, passwordEncoder.encode(password), name);
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

    public List<User> getUsersByPartialName(final String query) {
        return userDataService.findUsersByClosestMatchName(query, USERS_PAGE_SIZE);
    }

    public long getUserCount() {
        return userDataService.countUsers();
    }

    public Optional<Group> getGroupById(final long id) {
        return userDataService.findGroupById(id);
    }

    public Optional<GroupWithMembers> getGroupWithMembersById(final long id) {
        final var groupWithMembers = userDataService.findGroupWithMembersById(id);
        if (groupWithMembers.isEmpty()) {
            return Optional.empty();
        }
        if (!hasMemberAccess(groupWithMembers.get(), id)) {
            throw new UnauthorizedException(
                    "You can only get group members for groups you are a member of.");
        }
        return groupWithMembers;
    }

    public List<Group> getGroupsByPartialName(final String query) {
        return userDataService.findGroupsByClosestMatchName(query, GROUPS_PAGE_SIZE);
    }

    public GroupWithMembers saveGroup(final String name, final Collection<Long> initialMembers) {
        return userDataService.saveGroup(name, initialMembers);
    }

    private boolean hasMemberAccess(final GroupWithMembers group, final long id) {
        return group.members().stream().anyMatch(member -> member.id() == id);
    }

    public GroupWithMembers joinGroup(final long groupId, final long userId) {
        return userDataService.joinGroup(groupId, userId);
    }
}
