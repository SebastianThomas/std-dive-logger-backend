package ch.sthomas.stddivelogger.ws.services.feign;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
        value = "importerClient",
        url = "${ch.sthomas.stddivelogger.ws.services.feign.importer.url}",
        path = "v1/import",
        configuration = ImporterFeignClientConfiguration.class)
public interface ImporterFeignClient {
    @RequestMapping(method = RequestMethod.POST, path = "")
    Dive upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("uploadBody") UploadDiveBody uploadDiveBody);
}
