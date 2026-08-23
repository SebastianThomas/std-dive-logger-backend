package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
        @JacksonXmlElementWrapper(localName = "diveLogRecords")
                @JacksonXmlProperty(localName = "diveLogRecord")
                List<ShearwaterDiveLogRecord> diveLogRecords) {}
