package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;

import java.util.stream.Stream;

public record UploadDiveResultStreaming(Stream<SimplifiedDive> dives, Stream<String> errors) {
    public UploadDiveResult toResult() {
        return new UploadDiveResult(dives.toList(), errors.toList());
    }

    public UploadDiveResultStreaming concat(final UploadDiveResultStreaming b) {
        return new UploadDiveResultStreaming(
                Stream.concat(dives, b.dives), Stream.concat(errors, b.errors));
    }
}
