package ch.sthomas.stddivelogger.data.model;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(int pageSize, int totalPages, List<T> result) {
    public static <T, R> PagedResponse<R> of(final Page<T> elements, final Function<T, R> mapper) {
        final var pageSize = elements.getPageable().getPageSize();
        final var totalPages = elements.getTotalPages();
        return new PagedResponse<>(pageSize, totalPages, elements.stream().map(mapper).toList());
    }

    public <R> PagedResponse<R> map(final Function<T, R> mapper) {
        return new PagedResponse<>(pageSize, totalPages, result.stream().map(mapper).toList());
    }
}
