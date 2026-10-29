package ch.sthomas.stddivegastracker.model.report;

import ch.sthomas.stddivegastracker.model.vessel.VesselDetail.LossReport;
import ch.sthomas.stddivegastracker.model.vessel.VesselSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** API.md §6 losses: every closed bank with its loss report (DOMAIN §6), newest closing first. */
public record LossesReport(List<Entry> vessels) {

    public LossesReport {
        vessels = List.copyOf(vessels);
    }

    @Schema(name = "LossesReportEntry")
    public record Entry(VesselSummary vessel, LossReport report) {}
}
