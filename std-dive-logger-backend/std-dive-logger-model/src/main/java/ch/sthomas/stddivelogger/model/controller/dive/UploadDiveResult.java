package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;

import java.util.List;

public record UploadDiveResult(List<SimplifiedDive> dives, List<String> errors) {
    public UploadDiveResult() {
        this(List.of());
    }

    public UploadDiveResult(final List<SimplifiedDive> dives) {
        this(dives, List.of());
    }
}
