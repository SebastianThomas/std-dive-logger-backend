package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.report.ConsumptionReport;
import ch.sthomas.stddivegastracker.model.report.LossesReport;
import ch.sthomas.stddivegastracker.model.report.PolicyComparison;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.ReportService;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** API.md §6 "Reports"; {@code from}/{@code to} are epoch millis, {@code to} exclusive. */
@RestController
@RequestMapping("/v1/teams/{teamId}/reports")
public final class ReportController {

    private final ReportService reportService;

    public ReportController(final ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/consumption")
    public ConsumptionReport consumption(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestParam(required = false) final @Nullable Long from,
            @RequestParam(required = false) final @Nullable Long to) {
        return reportService.consumption(
                teamId,
                user,
                from == null ? null : Instant.ofEpochMilli(from),
                to == null ? null : Instant.ofEpochMilli(to));
    }

    @GetMapping("/losses")
    public LossesReport losses(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return reportService.losses(teamId, user);
    }

    @GetMapping("/policy-comparison")
    public PolicyComparison policyComparison(
            @AuthenticationPrincipal final GasUser user, @PathVariable final long teamId) {
        return reportService.policyComparison(teamId, user);
    }
}
