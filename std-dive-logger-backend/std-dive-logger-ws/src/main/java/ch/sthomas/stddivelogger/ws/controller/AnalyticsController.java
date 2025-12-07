package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/dives/analytics")
public class AnalyticsController {
    private final AnalyticsDataService analyticsDataService;

    public AnalyticsController(final AnalyticsDataService analyticsDataService) {
        this.analyticsDataService = analyticsDataService;
    }

    @GetMapping("/depth-variance")
    public List<AnalyticsDepthVarianceResponse> depthVarianceByDive(
            @AuthenticationPrincipal final User user,
            @RequestParam(name = "id") final long diveId) {
        if (user == null) {
            throw new UnauthorizedException("Login to find analytics for dive " + diveId);
        }
        return analyticsDataService.findDepthVarianceAnalyticsByDiveId(user.id(), diveId);
    }
}
