package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterUddfReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ImportService {
    private final FitReaderService fitReaderService;
    private final ShearwaterUddfReaderService shearwaterUddfReaderService;
    private final SubsurfaceXmlReaderService subsurfaceXmlReaderService;

    public ImportService(
            final FitReaderService fitReaderService,
            final ShearwaterUddfReaderService shearwaterUddfReaderService,
            final SubsurfaceXmlReaderService subsurfaceXmlReaderService) {
        this.fitReaderService = fitReaderService;
        this.shearwaterUddfReaderService = shearwaterUddfReaderService;
        this.subsurfaceXmlReaderService = subsurfaceXmlReaderService;
    }

    public UploadDiveResult uploadDive(
            final User user, final List<MultipartFile> files, final UploadDiveBody body) {
        record UploadDiveResultStreaming(Stream<SimplifiedDive> dives, Stream<String> errors) {
            UploadDiveResult toResult() {
                return new UploadDiveResult(dives.toList(), errors.toList());
            }

            public UploadDiveResultStreaming concat(final UploadDiveResultStreaming b) {
                return new UploadDiveResultStreaming(
                        Stream.concat(dives, b.dives), Stream.concat(errors, b.errors));
            }
        }
        return files.stream()
                .map(
                        file -> {
                            try {
                                return new UploadDiveResultStreaming(
                                        importFile(
                                                user,
                                                file.getOriginalFilename(),
                                                body,
                                                file.getInputStream())
                                                .stream(),
                                        Stream.of());
                            } catch (final IOException e) {
                                return new UploadDiveResultStreaming(
                                        Stream.of(), Stream.of(e.getMessage()));
                            }
                        })
                .reduce(UploadDiveResultStreaming::concat)
                .map(UploadDiveResultStreaming::toResult)
                .orElse(new UploadDiveResult(List.of(), List.of("No dive uploaded")));
    }

    List<SimplifiedDive> importFile(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        final var fileType = UploadFileType.fromFilename(filename);
        return switch (fileType) {
            case NONE ->
                    throw new IllegalArgumentException(
                            MessageFormat.format(
                                    "Could not resolve file type for filename {0}, supported extensions: {1}",
                                    filename, UploadFileType.supportedExtensions()));
            case UDDF_SHEARWATER ->
                    List.of(
                            shearwaterUddfReaderService.importUddf(
                                    user, filename, body, inputStream));
            case FIT_GARMIN ->
                    List.of(fitReaderService.readFitAndSaveDive(user, filename, body, inputStream));
            case XML_SUBSURFACE ->
                    subsurfaceXmlReaderService.importSubsurfaceXml(
                            user, filename, body, inputStream);
        };
    }
}
