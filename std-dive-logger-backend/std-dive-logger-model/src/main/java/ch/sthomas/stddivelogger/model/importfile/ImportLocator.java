package ch.sthomas.stddivelogger.model.importfile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Where in a stored file a dive / profile is: the dive's position ({@code entry}, UDDF /
 * Subsurface) or id ({@code id}, Shearwater Cloud DB), plus the profile's index within that dive.
 * All null for a single-dive file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImportLocator(
        @Nullable Integer entry, @Nullable String id, @Nullable Integer profile) {

    public static final ImportLocator WHOLE_FILE = new ImportLocator(null, null, null);

    public static ImportLocator ofEntry(final int entry) {
        return new ImportLocator(entry, null, null);
    }

    public static ImportLocator ofId(final String id) {
        return new ImportLocator(null, id, null);
    }

    public ImportLocator withProfile(final int profileIndex) {
        return new ImportLocator(entry, id, profileIndex);
    }

    /** The dive part only - what a dive-level link stores. */
    @JsonIgnore
    public ImportLocator dive() {
        return profile == null ? this : new ImportLocator(entry, id, null);
    }

    public boolean sameDive(final ImportLocator other) {
        return Objects.equals(entry, other.entry) && Objects.equals(id, other.id);
    }

    @JsonIgnore
    public int profileIndex() {
        return profile == null ? 0 : profile;
    }
}
