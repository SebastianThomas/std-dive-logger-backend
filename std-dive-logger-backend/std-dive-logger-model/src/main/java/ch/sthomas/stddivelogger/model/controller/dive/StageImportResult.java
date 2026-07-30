package ch.sthomas.stddivelogger.model.controller.dive;

import java.util.List;

public record StageImportResult(List<PendingImportSummary> staged, List<String> errors) {
    public StageImportResult(final List<PendingImportSummary> staged) {
        this(staged, List.of());
    }
}
