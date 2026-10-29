package ch.sthomas.stddivegastracker.model;

import java.util.List;
import java.util.function.Function;

/** API.md "Conventions": a page of a list; {@code page} is 0-based. Only the fills list pages. */
public record Page<T>(List<T> result, int page, int size, long totalElements, int totalPages) {

    public Page {
        result = List.copyOf(result);
    }

    public <R> Page<R> map(final Function<? super T, ? extends R> mapper) {
        return new Page<>(
                result.stream().<R>map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
