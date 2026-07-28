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
    FIT_GARMIN("fit"),
    XML_SUBSURFACE("xml");

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
