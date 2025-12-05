package ch.sthomas.stddivelogger.model.exception;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class MissingDiveSiteValueException extends MissingValueException {
    private final String name;

    public MissingDiveSiteValueException(final String name) {
        super(
                MissingValueField.DIVE_SITE,
                "Could not find Dive Site by name.",
                List.of(Pair.of("name", name)));
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
