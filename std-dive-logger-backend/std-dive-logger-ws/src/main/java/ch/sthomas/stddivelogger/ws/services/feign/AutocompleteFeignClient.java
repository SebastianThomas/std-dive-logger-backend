package ch.sthomas.stddivelogger.ws.services.feign;

import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(
        value = "autocompleteClient",
        url = "${ch.sthomas.stddivelogger.ws.services.feign.autocomplete.url}",
        path = "v1/autocomplete",
        configuration = ImporterFeignClientConfiguration.class)
public interface AutocompleteFeignClient {
    @RequestMapping(method = RequestMethod.GET, path = "/number")
    int getDiveNumberAutocomplete(User user);
}
