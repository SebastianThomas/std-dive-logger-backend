package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.UserDiveStats;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.StatsService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/stats")
public class StatsController {
    private final DiveService diveService;
    private final StatsService statsService;

    public StatsController(final DiveService diveService, StatsService statsService) {
        this.diveService = diveService;
        this.statsService = statsService;
    }

    @GetMapping(path = "")
    public UserDiveStats getStatsForUser(@AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dive stats");
        }
        return statsService.getStatsForUser(user);
    }

    @GetMapping(path = "/buddy")
    public Map<String, UserDiveStats> getStatsForUserByBuddy(
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dive stats");
        }
        return statsService.getStatsForUserByBuddy(user);
    }

    @GetMapping(path = "/dive-site")
    public Set<Map.Entry<DiveSite, UserDiveStats>> getStatsForUserByDiveSite(
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dive stats");
        }
        return statsService.getStatsForUserByDiveSite(user).entrySet();
    }

    @GetMapping(path = "/year")
    public Map<Integer, UserDiveStats> getStatsForUserByYear(
            @AuthenticationPrincipal final User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view your dive stats");
        }
        return statsService.getStatsForUserByYear(user);
    }
}
