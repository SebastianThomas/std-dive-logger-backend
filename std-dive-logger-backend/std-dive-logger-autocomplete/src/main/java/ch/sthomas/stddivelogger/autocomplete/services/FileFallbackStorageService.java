package ch.sthomas.stddivelogger.autocomplete.services;

import ch.sthomas.stddivelogger.data.service.storage.StorageService;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class FileFallbackStorageService implements StorageService {
    @Override
    public void upload(
            final String path,
            final InputStream output,
            final String contentType,
            final int contentLength)
            throws IOException {
        throw new NotImplementedException();
    }

    @Override
    public String baseUrl() {
        throw new NotImplementedException();
    }
}
