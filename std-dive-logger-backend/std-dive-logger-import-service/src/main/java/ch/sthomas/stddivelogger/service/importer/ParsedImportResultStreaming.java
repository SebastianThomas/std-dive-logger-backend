package ch.sthomas.stddivelogger.service.importer;

import java.util.List;
import java.util.stream.Stream;

/** Mirrors {@code UploadDiveResultStreaming}, but for parsed-not-yet-persisted imports. */
public record ParsedImportResultStreaming(Stream<ParsedImport> parsed, Stream<String> errors) {
    public record Result(List<ParsedImport> parsed, List<String> errors) {}

    public Result toResult() {
        return new Result(parsed.toList(), errors.toList());
    }

    public ParsedImportResultStreaming concat(final ParsedImportResultStreaming b) {
        return new ParsedImportResultStreaming(
                Stream.concat(parsed, b.parsed), Stream.concat(errors, b.errors));
    }
}
