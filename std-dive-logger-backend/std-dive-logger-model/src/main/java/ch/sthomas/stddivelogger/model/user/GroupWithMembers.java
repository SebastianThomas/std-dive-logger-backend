package ch.sthomas.stddivelogger.model.user;

import java.util.Collection;

public record GroupWithMembers(long id, String name, Collection<FrontendUser> members) {}
