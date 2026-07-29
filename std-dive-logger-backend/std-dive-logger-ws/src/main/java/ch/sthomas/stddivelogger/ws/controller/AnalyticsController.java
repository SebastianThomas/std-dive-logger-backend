package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceResponse;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileRatesResponse;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/dives/analytics")
@Validated
public class AnalyticsController {
    private final AnalyticsDataService analyticsDataService;

    public AnalyticsController(final AnalyticsDataService analyticsDataService) {
        this.analyticsDataService = analyticsDataService;
    }

    @GetMapping("/segments")
    public List<DiveProfileSegmentWithId> segments(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Login to find analytics for dive " + diveId);
        }
        return analyticsDataService.findSegmentsByDiveId(user, diveId, false);
    }

    @GetMapping("/depth-variance")
    public List<AnalyticsDepthVarianceResponse> depthVarianceByDive(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Login to find analytics for dive " + diveId);
        }
        return analyticsDataService.findDepthVarianceAnalyticsByDiveId(user.id(), diveId);
    }

    @GetMapping("/rates")
    public List<DiveProfileRatesResponse> rates(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(name = "id") @Positive final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Login to find analytics for dive " + diveId);
        }
        return analyticsDataService.findRatesByDiveId(user, diveId);
    }
}
