package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShearwaterDiveLog(
        int number,
        @JacksonXmlProperty String startDate,
        @JacksonXmlProperty String endDate,
        double maxDepth,
        // Dive-level "official" total (matches startDate/endDate to within a few seconds) - close
        // to but not exactly the last record's currentTime, the same small vendor-summary-vs-raw-
        // samples gap already seen for Suunto/FIT. Used only for the staged-import preview guess;
        // the real profile is built from the record stream's own currentTime values.
        long maxTime,
        double endCns,
        String computerSerial,
        String computerModel,
        // How the device calculated deco / CNS - see ShearwaterXmlReaderService#decoSettings.
        @Nullable Integer gfMin,
        @Nullable Integer gfMax,
        @Nullable Integer decoModel,
        @Nullable Integer vpmbConservatism,
        @Nullable Double startCns,
        @Nullable String computerFirmware,
        @Nullable String computerSoftwareVersion,
        @Nullable Double startSurfacePressure,
        @Nullable Double endSurfacePressure,
        @Nullable Integer logVersion,
        @Nullable Integer product,
        @Nullable Long features,
        @JacksonXmlElementWrapper(localName = "diveLogRecords")
                @JacksonXmlProperty(localName = "diveLogRecord")
                List<ShearwaterDiveLogRecord> diveLogRecords) {

    public ShearwaterDiveLog(
            final int number,
            final String startDate,
            final String endDate,
            final double maxDepth,
            final long maxTime,
            final double endCns,
            final String computerSerial,
            final String computerModel,
            final List<ShearwaterDiveLogRecord> diveLogRecords) {
        this(
                number,
                startDate,
                endDate,
                maxDepth,
                maxTime,
                endCns,
                computerSerial,
                computerModel,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                diveLogRecords);
    }
}
