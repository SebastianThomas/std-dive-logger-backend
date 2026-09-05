package ch.sthomas.stddivelogger.model.importer.shearwater;

import org.jspecify.annotations.Nullable;

/**
 * One row of Shearwater Cloud's {@code dive_details} table joined to its {@code log_data} row - the
 * diver-entered metadata around a dive, plus the gzipped binary log that carries the actual
 * profile. Only the columns this project can actually use are modelled; {@code dive_details} has
 * ~55 more (weather, workload, symptoms, ...) with no counterpart here.
 *
 * <p>{@code diveDate} is the local wall-clock string the app shows, matching the (timezone-less)
 * clock reading inside {@code logData} exactly - see {@link ShearwaterPnfLog#start()}.
 *
 * <p>{@code site} is almost always null in practice and {@code location} is what the diver actually
 * types in the app's "Location" field, so the reader prefers {@code location} and falls back to
 * {@code site}.
 */
public record ShearwaterDbDive(
        String diveId,
        @Nullable String fileName,
        @Nullable String diveDate,
        @Nullable String serialNumber,
        @Nullable String diveNumber,
        @Nullable String location,
        @Nullable String site,
        @Nullable String buddy,
        @Nullable String notes,
        @Nullable String visibility,
        @Nullable String environment,
        @Nullable String weight,
        @Nullable String tankSize,
        @Nullable String apparatus,
        @Nullable String tankProfileData,
        byte[] logData) {}
