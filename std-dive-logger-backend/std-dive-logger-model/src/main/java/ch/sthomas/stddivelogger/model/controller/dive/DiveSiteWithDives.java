package ch.sthomas.stddivelogger.model.controller.dive;

import static ch.sthomas.stddivelogger.utils.MoreStreamUtils.zip;

import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@code diveCount} is always populated (cheap - just the length of the dive list at this site).
 * {@code diveInfo} - the actual per-dive id/number/identifier list - is null when the caller has
 * too many sites to inline all of them (see {@code DiveService#getSitesByUser}); the frontend
 * fetches it lazily per site on demand in that case (e.g. only when a map marker's popup opens),
 * via {@code GET /v1/dives/sites/{id}/dives}. Below that threshold, it's always present so the
 * common case (most users have a few dozen to a few hundred sites) needs no extra round trip.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveSiteWithDives<T>(T site, long diveCount, @Nullable List<BasicDiveInfo> diveInfo) {
    public static <T> DiveSiteWithDives<T> of(
            final T site,
            final List<Long> ids,
            final List<Long> numbers,
            final List<String> identifiers) {
        final var diveInfo =
                zip(BasicDiveInfo::new, ids.stream(), numbers.stream(), identifiers.stream())
                        .toList();
        return new DiveSiteWithDives<>(site, diveInfo.size(), diveInfo);
    }

    /** Same site, but with {@code diveInfo} stripped to just its count - see the class doc. */
    public DiveSiteWithDives<T> withoutDiveInfo() {
        return new DiveSiteWithDives<>(site, diveCount, null);
    }
}
