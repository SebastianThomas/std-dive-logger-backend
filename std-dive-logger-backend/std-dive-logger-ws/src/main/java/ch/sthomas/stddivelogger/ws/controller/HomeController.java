package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.dive.home.HomeDashboard;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.HomeService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the logged-in home page. Non-nullable principal (like {@link StatsController}) - security
 * 401s an anonymous caller before this runs - so the injected {@link User} is used directly, no
 * re-fetch.
 */
@RestController
@RequestMapping("/v1/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(final HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping(path = "")
    public HomeDashboard getHome(@AuthenticationPrincipal final User user) {
        return homeService.forUser(user);
    }
}
