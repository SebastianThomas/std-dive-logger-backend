package ch.sthomas.stddivelogger.utils;

import static com.google.common.base.Preconditions.checkNotNull;

import static java.lang.Math.min;

import org.apache.commons.lang3.function.TriFunction;
import org.jspecify.annotations.Nullable;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MoreStreamUtils {
    private MoreStreamUtils() {}

    public static <
                    A extends @Nullable Object,
                    B extends @Nullable Object,
                    C extends @Nullable Object,
                    R extends @Nullable Object>
            Stream<R> zip(
                    final TriFunction<? super A, ? super B, ? super C, R> function,
                    final Stream<A> streamA,
                    final Stream<B> streamB,
                    final Stream<C> streamC) {
        checkNotNull(streamA);
        checkNotNull(streamB);
        checkNotNull(streamC);
        checkNotNull(function);
        final var isParallel =
                streamA.isParallel()
                        || streamB.isParallel()
                        || streamC.isParallel(); // same as Stream.concat
        final var splitrA = streamA.spliterator();
        final var splitrB = streamB.spliterator();
        final var splitrC = streamC.spliterator();
        final var characteristics =
                splitrA.characteristics()
                        & splitrB.characteristics()
                        & splitrC.characteristics()
                        & (Spliterator.SIZED | Spliterator.ORDERED);
        final var itrA = Spliterators.iterator(splitrA);
        final var itrB = Spliterators.iterator(splitrB);
        final var itrC = Spliterators.iterator(splitrC);
        return StreamSupport.stream(
                        new Spliterators.AbstractSpliterator<R>(
                                min(
                                        min(splitrA.estimateSize(), splitrB.estimateSize()),
                                        splitrC.estimateSize()),
                                characteristics) {
                            @Override
                            public boolean tryAdvance(final Consumer<? super R> action) {
                                if (itrA.hasNext() && itrB.hasNext()) {
                                    action.accept(
                                            function.apply(itrA.next(), itrB.next(), itrC.next()));
                                    return true;
                                }
                                return false;
                            }
                        },
                        isParallel)
                .onClose(streamA::close)
                .onClose(streamB::close);
    }
}
