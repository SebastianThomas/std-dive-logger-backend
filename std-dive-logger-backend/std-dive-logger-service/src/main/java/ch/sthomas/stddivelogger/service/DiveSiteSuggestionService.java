package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.data.service.DiveSiteSuggestionDataService;
import ch.sthomas.stddivelogger.model.dive.DiveSiteSuggestion;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiveSiteSuggestionService {

    private final DiveSiteSuggestionDataService dataService;

    public DiveSiteSuggestionService(final DiveSiteSuggestionDataService dataService) {
        this.dataService = dataService;
    }

    public List<DiveSiteSuggestion> suggest(
            final User user,
            final @Nullable Double lat,
            final @Nullable Double lon,
            final @Nullable Double maxDistanceKm,
            final int limit) {
        return dataService.suggest(user.id(), lat, lon, maxDistanceKm, limit);
    }
}
