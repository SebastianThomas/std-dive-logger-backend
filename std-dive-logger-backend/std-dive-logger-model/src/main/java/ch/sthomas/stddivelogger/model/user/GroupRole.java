package ch.sthomas.stddivelogger.model.user;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum GroupRole {
    MEMBER,
    ADMIN,
    REQUESTED,
    DENIED;

    private static final Map<String, GroupRole> roles =
            Arrays.stream(GroupRole.values())
                    .map(v -> Map.entry(v.name(), v))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public static Optional<GroupRole> find(final String roleString) {
        return Optional.ofNullable(roles.get(roleString));
    }
}
