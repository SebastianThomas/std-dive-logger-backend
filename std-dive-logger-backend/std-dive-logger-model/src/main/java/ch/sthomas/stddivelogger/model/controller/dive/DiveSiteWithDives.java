package ch.sthomas.stddivelogger.model.controller.dive;

import static ch.sthomas.stddivelogger.utils.MoreStreamUtils.zip;

import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;

import java.util.List;

public record DiveSiteWithDives<T>(T site, List<BasicDiveInfo> diveIds) {
    public static <T> DiveSiteWithDives<T> of(
            final T site,
            final List<Long> ids,
            final List<Long> numbers,
            final List<String> identifiers) {
        return new DiveSiteWithDives<>(
                site,
                zip(BasicDiveInfo::new, ids.stream(), numbers.stream(), identifiers.stream())
                        .toList());
    }
}
