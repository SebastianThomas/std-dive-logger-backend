package ch.sthomas.stddivelogger.service.importer.divesoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Hits the real wetnotes.com/Auth0 over the network - run manually after any changes")
class DivesoftConfigServiceTest {

    @Test
    void fetchesRealConfigFromWetnotes() {
        final var service =
                new DivesoftConfigService(ObjectMapperUtils.objectMapperBuilder(b -> {}).build());
        final var config = service.getConfig();

        assertEquals("wetnotes.eu.auth0.com", config.domain());
        assertEquals("uH6deVTcYzp1kg4TLHIMEQ2SZ5edrn8c", config.clientId());
        assertEquals("Username-Password-Authentication", config.realm());
        assertEquals("https://wetnotes.com/api", config.audience());
        assertEquals("https://divesoft-app.foxmedia.cz/api/", config.apiBaseUrl());
        assertFalse(config.clientSecret().isBlank());
    }
}
