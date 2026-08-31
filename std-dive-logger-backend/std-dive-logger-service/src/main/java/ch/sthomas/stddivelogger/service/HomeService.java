package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.HomeDataService;
import ch.sthomas.stddivelogger.model.dive.home.HomeDashboard;
import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.stereotype.Service;

/** Pass-through to {@link HomeDataService} - matches {@link StatsService}'s shape. */
@Service
public class HomeService {

    private final HomeDataService homeDataService;

    public HomeService(final HomeDataService homeDataService) {
        this.homeDataService = homeDataService;
    }

    public HomeDashboard forUser(final User user) {
        return homeDataService.forUser(user.id(), user.name());
    }
}
