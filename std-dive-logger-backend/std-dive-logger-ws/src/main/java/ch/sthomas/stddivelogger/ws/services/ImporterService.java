package ch.sthomas.stddivelogger.ws.services;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.ws.services.feign.ImporterFeignClient;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImporterService {
    private final ImporterFeignClient importerFeignClient;

    public ImporterService(final ImporterFeignClient importerFeignClient) {
        this.importerFeignClient = importerFeignClient;
    }

    public Dive uploadDive(final MultipartFile file, final UploadDiveBody body) {
        return importerFeignClient.upload(file, body);
    }
}
