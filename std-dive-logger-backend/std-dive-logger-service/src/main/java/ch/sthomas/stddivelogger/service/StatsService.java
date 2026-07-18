package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.StatsDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.stats.StatsFilters;
import ch.sthomas.stddivelogger.model.dive.stats.StatsGranularity;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeries;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.user.User;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    public List<UserDiveStatsBy<DiveSite>> getStatsForUserByDiveSite(final User user) {
        return statsDataService.getStatsByDiveSite(user).entrySet().stream()
                .map(
                        e ->
                                new UserDiveStatsBy<>(
                                        diveDataService.findDiveSiteById(e.getKey()).orElseThrow(),
                                        e.getValue().withSites(null)))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public List<UserDiveStatsBy<String>> getStatsForUserByBuddy(final User user) {
        return statsDataService.getStatsByBuddy(user).stream()
                .map(u -> new UserDiveStatsBy<>(u.key(), u.stats().withBuddies(null)))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public List<UserDiveStatsBy<BaseConfiguration>> getStatsForUserByBaseConfiguration(
            final User user) {
        return statsDataService.getStatsByBaseConfiguration(user);
    }

    public List<UserDiveStatsBy<TagDefinition>> getStatsForUserByTag(final User user) {
        return statsDataService.getStatsByTag(user);
    }

    public UserDiveStats getStatsForUserByTagFilter(final User user, final Collection<Long> tagIds) {
        return statsDataService.computeStatsForTagFilter(user, tagIds);
    }

    public StatsTimeSeries getTimeSeries(
            final User user, final StatsGranularity granularity, final StatsFilters filters) {
        return statsDataService.getTimeSeries(user, granularity, filters);
    }
}
