package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.user.User;

import org.junit.jupiter.api.Test;

import java.util.List;

public class DiveServiceTest {
    @Test
    void testGetDive() {
        final var diveDataService = mock(DiveDataService.class);
        final var userDataService = mock(UserDataService.class);
        final var service = new DiveService(diveDataService, userDataService);
        assertEquals(List.of(), service.getDivesForUser(mock(User.class)));
    }
}
