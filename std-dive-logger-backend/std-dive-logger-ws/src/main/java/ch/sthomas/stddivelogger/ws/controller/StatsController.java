package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.stats.StatsBreakdownDimension;
import ch.sthomas.stddivelogger.model.dive.stats.StatsFilters;
import ch.sthomas.stddivelogger.model.dive.stats.StatsGranularity;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeries;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.StatsService;

import jakarta.annotation.Nullable;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/stats")
public class StatsController {
    private final StatsService statsService;

    public StatsController(final StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping(path = "")
    public UserDiveStats getStatsForUser(@AuthenticationPrincipal final User user) {
        return statsService.getStatsForUser(user);
    }

    @GetMapping(path = "/buddy")
    public List<UserDiveStatsBy<String>> getStatsForUserByBuddy(
            @AuthenticationPrincipal final User user) {
        return statsService.getStatsForUserByBuddy(user);
    }

    @GetMapping(path = "/dive-site")
    public List<UserDiveStatsBy<DiveSite>> getStatsForUserByDiveSite(
            @AuthenticationPrincipal final User user) {
        return statsService.getStatsForUserByDiveSite(user);
    }

    @GetMapping(path = "/year")
    public Map<Integer, UserDiveStats> getStatsForUserByYear(
            @AuthenticationPrincipal final User user) {
        return statsService.getStatsForUserByYear(user);
    }

    @GetMapping(path = "/base-configuration")
    public List<UserDiveStatsBy<BaseConfiguration>> getStatsForUserByConfiguration(
            @AuthenticationPrincipal final User user) {
        return statsService.getStatsForUserByBaseConfiguration(user);
    }

    @GetMapping(path = "/by-tag")
    public List<UserDiveStatsBy<TagDefinition>> getStatsForUserByTag(
            @AuthenticationPrincipal final User user) {
        return statsService.getStatsForUserByTag(user);
    }

    @GetMapping(path = "/tags")
    public UserDiveStats getStatsForUserByTagFilter(
            @AuthenticationPrincipal final User user,
            @RequestParam final List<Long> tagIds) {
        return statsService.getStatsForUserByTagFilter(user, tagIds);
    }

    @GetMapping(path = "/timeseries")
    public StatsTimeSeries getTimeSeries(
            @AuthenticationPrincipal final User user,
            @RequestParam(defaultValue = "PER_DIVE") final StatsGranularity granularity,
            @RequestParam(required = false) final List<Long> tagIds,
            @RequestParam(required = false) final Long diveSiteId,
            @RequestParam(required = false) final Long suitId,
            @RequestParam(required = false) final Long ccrUnitId,
            @RequestParam(required = false) final BaseConfiguration baseConfiguration,
            @RequestParam(required = false) @Nullable final String query,
            @RequestParam(required = false) @Nullable final StatsBreakdownDimension breakdownBy) {
        return statsService.getTimeSeries(
                user,
                granularity,
                new StatsFilters(tagIds, diveSiteId, suitId, ccrUnitId, baseConfiguration, query),
                breakdownBy);
    }
}
