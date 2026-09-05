package ch.sthomas.stddivelogger.model.dive;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One "suggest a dive site" result: the site, its score, and why it was picked. {@code topPick} is
 * the clear winner - or two, in case of a near-tie - the rest is a random-sized sample of the
 * remaining scored candidates, not necessarily the strict runners-up.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveSiteSuggestion(
        DiveSite site,
        double score,
        List<String> reasons,
        @Nullable Integer daysSinceLastVisit,
        @Nullable Double avgVisibilityM,
        @Nullable Integer recentDiverCount30d,
        int totalDives,
        @Nullable Double distanceKm,
        boolean topPick) {}
