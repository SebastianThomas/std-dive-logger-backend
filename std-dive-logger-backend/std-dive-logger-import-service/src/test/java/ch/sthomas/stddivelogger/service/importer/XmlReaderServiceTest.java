package ch.sthomas.stddivelogger.service.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterXmlReaderService;
import ch.sthomas.stddivelogger.service.importer.subsurface.SubsurfaceXmlReaderService;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** ".xml" isn't necessarily Subsurface - see XmlReaderService/ShearwaterXmlReaderService. */
class XmlReaderServiceTest {
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);

    private static DiveService diveServiceReturningComputer() {
        final var diveService = mock(DiveService.class);
        when(diveService.getOrCreateDiveComputer(any(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocation ->
                                new DiveComputer(
                                        1L,
                                        new DiveComputerManufacturer(1L, invocation.getArgument(1)),
                                        invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        null));
        return diveService;
    }

    private final XmlReaderService service =
            new XmlReaderService(
                    new SubsurfaceXmlReaderService(
                            ObjectMapperUtils.xmlMapperBuilder(c -> {}).build(),
                            diveServiceReturningComputer()),
                    new ShearwaterXmlReaderService(
                            ObjectMapperUtils.xmlMapperBuilder(c -> {}).build(),
                            diveServiceReturningComputer()));

    private ParsedImportResultStreaming.Result parse(final String xml) throws IOException {
        return service.parse(
                        user,
                        "test.xml",
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                .reduce(ParsedImportResultStreaming::concat)
                .orElseThrow()
                .toResult();
    }

    @Test
    void notXmlAtAllFailsWithAGenericMessage() {
        assertThatThrownBy(() -> parse("this is not xml at all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.xml");
    }

    @Test
    void aSubsurfaceShapedExportIsDispatchedToTheSubsurfaceReader() throws IOException {
        final var xml =
                """
                <divelog>
                  <divesites uuid="site-1" name="Test Site" gps="1.0 2.0"/>
                  <dives>
                    <dive number="1" date="2024-01-01" time="10:00:00" duration="0:10 min" \
                rating="0" divesiteid="site-1" visibility="0" current="0">
                      <buddy>Test Buddy</buddy>
                      <cylinder description="AL80" o2="21%"/>
                      <divecomputer model="Test 123" deviceid="1" diveid="1" date="2024-01-01" \
                time="10:00:00" duration="0:10 min">
                        <event time="0:00 min" type="0" name="gaschange" cylinder="0"/>
                        <sample time="0:00 min" depth="0.0 m"/>
                      </divecomputer>
                    </dive>
                  </dives>
                </divelog>
                """;

        final var result = parse(xml);

        assertThat(result.parsed()).hasSize(1);
        assertThat(result.parsed().getFirst().source())
                .isEqualTo(PendingImportSource.XML_SUBSURFACE);
    }

    @Test
    void aShearwaterNativeExportIsDispatchedToTheShearwaterReader() throws IOException {
        final var xml =
                """
                <?xml version="1.0" encoding="utf-16"?>
                <dive version="3">
                  <diveLog>
                    <number>1</number>
                    <maxDepth>10.0</maxDepth>
                    <maxTime>60</maxTime>
                    <endCns>0</endCns>
                    <startDate>1/1/2024 10:00:00 AM</startDate>
                    <computerSerial>TESTSERIAL</computerSerial>
                    <computerModel>2</computerModel>
                    <diveLogRecords>
                      <diveLogRecord>
                        <currentTime>0</currentTime>
                        <currentDepth>1.0</currentDepth>
                        <waterTemp>20</waterTemp>
                        <currentNdl>99</currentNdl>
                        <ttsMins>0</ttsMins>
                        <firstStopDepth>0</firstStopDepth>
                        <firstStopTime>0</firstStopTime>
                        <fractionO2>0.21</fractionO2>
                        <fractionHe>0</fractionHe>
                        <averagePPO2>0.21</averagePPO2>
                        <currentCircuitSetting>OC/BO</currentCircuitSetting>
                      </diveLogRecord>
                    </diveLogRecords>
                  </diveLog>
                </dive>
                """;

        final var result = parse(xml);

        assertThat(result.parsed()).hasSize(1);
        assertThat(result.parsed().getFirst().source())
                .isEqualTo(PendingImportSource.XML_SHEARWATER);
    }
}
