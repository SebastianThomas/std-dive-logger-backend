package ch.sthomas.stddivelogger.model.controller.dive;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum UploadFileType {
    NONE(null),
    UDDF_SHEARWATER("uddf"),
    // Suunto FIT shares this extension - FitReaderService tells brands apart from the file's own
    // manufacturer field, not a separate UploadFileType.
    FIT_GARMIN("fit"),
    // Brand-neutral on purpose - XmlReaderService/JsonReaderService detect the actual format by
    // content (Subsurface vs. Shearwater's own native XML share this extension).
    XML("xml"),
    JSON("json"),
    DL7("zxu"),
    // Shearwater Cloud's own SQLite database (a whole logbook in one file). Generic extension by
    // necessity - ShearwaterDbReaderService checks for the app's tables before reading anything.
    DB("db");

    public static final Map<String, UploadFileType> fileTypesByExtension =
            Arrays.stream(UploadFileType.values())
                    .filter(t -> t.extension != null)
                    .collect(Collectors.toMap(f -> f.extension, Function.identity()));
    private final @Nullable String extension;

    UploadFileType(final @Nullable String extension) {
        this.extension = extension;
    }

    public static @Nullable UploadFileType fromFilename(final @Nullable String filename) {
        if (filename == null) {
            return NONE;
        }
        final var extension = filename.substring(filename.lastIndexOf('.') + 1);
        return fromExtension(extension);
    }

    public static @Nullable UploadFileType fromExtension(final @Nullable String extension) {
        if (extension == null) {
            return NONE;
        }
        return fileTypesByExtension.get(extension);
    }

    public static Collection<String> supportedExtensions() {
        return fileTypesByExtension.keySet();
    }
}
