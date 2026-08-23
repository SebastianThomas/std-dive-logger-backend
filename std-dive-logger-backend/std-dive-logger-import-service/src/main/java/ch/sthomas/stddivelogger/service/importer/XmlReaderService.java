package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterXmlReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * A ".xml" upload could be Subsurface's export or Shearwater's own native "Source File" export -
 * see ShearwaterXmlReaderService's doc comment for why the latter is preferred when available.
 * Dispatch is by content (the root element), never by filename/extension alone.
 */
@Service
public class XmlReaderService {
    private static final int SNIFF_PREFIX_BYTES = 4096;

    private final SubsurfaceXmlReaderService subsurfaceXmlReaderService;
    private final ShearwaterXmlReaderService shearwaterXmlReaderService;

    public XmlReaderService(
            final SubsurfaceXmlReaderService subsurfaceXmlReaderService,
            final ShearwaterXmlReaderService shearwaterXmlReaderService) {
        this.subsurfaceXmlReaderService = subsurfaceXmlReaderService;
        this.shearwaterXmlReaderService = shearwaterXmlReaderService;
    }

    public Stream<ParsedImportResultStreaming> parse(
            final User user, final String filename, final InputStream inputStream)
            throws IOException {
        final var bytes = inputStream.readAllBytes();
        final var prefix =
                new String(
                        bytes,
                        0,
                        Math.min(bytes.length, SNIFF_PREFIX_BYTES),
                        StandardCharsets.ISO_8859_1);
        if (ShearwaterXmlReaderService.matches(prefix)) {
            return Stream.of(
                    new ParsedImportResultStreaming(
                            Stream.of(shearwaterXmlReaderService.parse(user, filename, bytes)),
                            Stream.empty()));
        }
        if (prefix.contains("<divelog")) {
            return subsurfaceXmlReaderService.parse(
                    user, filename, new ByteArrayInputStream(bytes));
        }
        throw unrecognized(filename);
    }

    private static IllegalArgumentException unrecognized(final @Nullable String filename) {
        return new IllegalArgumentException(
                "Could not recognize " + filename + " as a supported dive log export.");
    }
}
