package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.StatsDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.user.User;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final StatsDataService statsDataService;
    private final DiveDataService diveDataService;

    public StatsService(final StatsDataService statsDataService, DiveDataService diveDataService) {
        this.statsDataService = statsDataService;
        this.diveDataService = diveDataService;
    }

    public UserDiveStats getStatsForUser(final User user) {
        final var old = statsDataService.computeStatsForUser(user);
        final var newCrit = statsDataService.computeStatsForUserCriteria(user);
        if (old.equals(newCrit)) {
            return newCrit;
        }
        throw new NotImplementedException("implementation is wrong");
    }

    public Map<Integer, UserDiveStats> getStatsForUserByYear(final User user) {
        return statsDataService.getStatsByYear(user);
    }

    public Map<DiveSite, UserDiveStats> getStatsForUserByDiveSite(final User user) {
        return statsDataService.getStatsByDiveSite(user).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                id -> diveDataService.findDiveSiteById(id.getKey()).orElseThrow(),
                                e -> e.getValue().withSites(null)));
    }

    public List<UserDiveStatsBy<String>> getStatsForUserByBuddy(final User user) {
        return statsDataService.getStatsByBuddy(user).stream()
                .map(u -> new UserDiveStatsBy<>(u.key(), u.stats().withBuddies(null)))
                .toList();
    }
}
