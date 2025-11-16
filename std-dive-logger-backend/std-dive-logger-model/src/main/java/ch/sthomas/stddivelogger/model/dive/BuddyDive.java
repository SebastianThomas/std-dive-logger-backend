package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.user.FrontendUser;

public record BuddyDive(FrontendUser buddy, long diveId) {}
