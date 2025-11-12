package ch.sthomas.stddivelogger.model.user;

import java.util.List;

public record GroupMembers(Group group, List<User> members) {}
