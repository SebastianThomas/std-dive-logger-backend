package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResult;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveResultStreaming;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.fit.FitReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;
import ch.sthomas.stddivelogger.service.importer.uddf.UddfReaderService;

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
    private final UddfReaderService uddfReaderService;
    private final SubsurfaceXmlReaderService subsurfaceXmlReaderService;

    public ImportService(
            final FitReaderService fitReaderService,
            final UddfReaderService uddfReaderService,
            final SubsurfaceXmlReaderService subsurfaceXmlReaderService) {
        this.fitReaderService = fitReaderService;
        this.uddfReaderService = uddfReaderService;
        this.subsurfaceXmlReaderService = subsurfaceXmlReaderService;
    }

    public UploadDiveResult uploadDive(
            final User user, final List<MultipartFile> files, final UploadDiveBody body) {
        return files.stream()
                .flatMap(file -> importFile(user, body, file))
                .reduce(UploadDiveResultStreaming::concat)
                .map(UploadDiveResultStreaming::toResult)
                .orElse(new UploadDiveResult(List.of(), List.of("No dive uploaded")));
    }

    private Stream<UploadDiveResultStreaming> importFile(
            final User user, final UploadDiveBody body, final MultipartFile file) {
        try {
            return importFile(user, file.getOriginalFilename(), body, file.getInputStream());
        } catch (final IOException e) {
            return Stream.of(
                    new UploadDiveResultStreaming(
                            Stream.empty(),
                            Stream.of(
                                    MessageFormat.format(
                                            "Could not import the file {0}",
                                            file.getOriginalFilename()))));
        }
    }

    private Stream<UploadDiveResultStreaming> importFile(
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
            case UDDF_SHEARWATER -> uddfReaderService.importUddf(user, filename, body, inputStream);
            case FIT_GARMIN ->
                    Stream.of(
                            fitReaderService.readFitAndSaveDive(user, filename, body, inputStream));
            case XML_SUBSURFACE ->
                    subsurfaceXmlReaderService.importSubsurfaceXml(
                            user, filename, body, inputStream);
        };
    }
}
