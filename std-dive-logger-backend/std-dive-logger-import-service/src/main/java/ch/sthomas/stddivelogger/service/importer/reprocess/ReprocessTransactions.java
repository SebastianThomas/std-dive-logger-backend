package ch.sthomas.stddivelogger.service.importer.reprocess;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/** One transaction per re-processed profile / dive link, so one failure doesn't undo the batch. */
@Component
public class ReprocessTransactions {

    @Transactional
    public <T> T run(final Supplier<T> work) {
        return work.get();
    }
}
