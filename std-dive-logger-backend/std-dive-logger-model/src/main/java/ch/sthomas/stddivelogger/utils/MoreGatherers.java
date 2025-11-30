package ch.sthomas.stddivelogger.utils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Gatherer;

public class MoreGatherers {
    private MoreGatherers() {}

    public static <T, K> Gatherer<T, Set<K>, T> distinctBy(final Function<T, K> f) {
        return Gatherer.ofSequential(
                HashSet::new,
                (map, obj, downstream) -> {
                    final var key = f.apply(obj);
                    if (!map.contains(key)) {
                        map.add(key);
                        downstream.push(obj);
                    }
                    return true;
                });
    }

    public static <T> Collector<T, ?, Optional<T>> lastWhile(final Predicate<? super T> predicate) {
        class Acc {
            boolean stillTaking = true;
            T last;

            void add(final T t) {
                if (stillTaking && predicate.test(t)) {
                    last = t;
                } else {
                    stillTaking = false;
                }
            }

            Acc combine(final Acc other) {
                // For parallel use, prefer the left accumulator unless it stopped early
                if (!stillTaking) return this;
                if (!other.stillTaking) {
                    this.last = other.last;
                    this.stillTaking = false;
                    return this;
                }
                // both are still taking → take the other's last
                this.last = other.last;
                return this;
            }

            Optional<T> finish() {
                return Optional.ofNullable(last);
            }
        }

        return Collector.of(Acc::new, Acc::add, Acc::combine, Acc::finish);
    }
}
