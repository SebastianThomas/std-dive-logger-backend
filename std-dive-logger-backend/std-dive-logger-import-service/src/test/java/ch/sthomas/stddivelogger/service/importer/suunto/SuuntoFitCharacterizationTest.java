package ch.sthomas.stddivelogger.service.importer.suunto;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmin.fit.Event;
import com.garmin.fit.FitDecoder;
import com.garmin.fit.FitMessages;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Objects;

/**
 * Documents, with real assertions (not just log output), exactly how a real Suunto EON Core FIT
 * export differs from what {@code FitReaderService} was originally written against (Garmin's own
 * FIT export). Found by decoding {@code suunto-eon-core-dive-1-deco.fit} directly with the
 * project's own FIT SDK (per explicit instruction to use the existing Java library rather than an
 * external tool) before writing any of the Suunto-handling code in {@code FitReaderService}/{@code
 * SuuntoJsonReaderService}. Each assertion here is a fact this project's Suunto support was
 * designed around - if any of these ever fails against a newer Suunto export, the assumption it's
 * checking needs re-examining, not just the assertion's expected value.
 */
class SuuntoFitCharacterizationTest {

    @Test
    void suuntoFitExportHasNoDiveSummaryMessageAtAll() throws Exception {
        // The single biggest structural difference from Garmin: FitReaderService.getSummary(List)
        // needs at least one DiveSummaryMesg (in fact more than one, to compute anything - see its
        // own doc comment) to produce anything other than Optional.empty(). Suunto's FIT encoder
        // never emits this message type, which is exactly why getSummary(FitMessages) exists as a
        // fallback layer - see FitReaderService.
        assertThat(decode().getDiveSummaryMesgs()).isEmpty();
    }

    @Test
    void suuntoFitExportHasExactlyOneSessionWithUsableStartAndEndTimes() throws Exception {
        final var sessions = decode().getSessionMesgs();
        assertThat(sessions).hasSize(1);
        final var session = sessions.getFirst();
        assertThat(session.getStartTime()).isNotNull();
        assertThat(session.getTimestamp()).isNotNull();
        assertThat(session.getTotalElapsedTime()).isEqualTo(3890.0f);
    }

    @Test
    void suuntoFitExportHasNoDeviceSerialNumber() throws Exception {
        // Confirmed empirically: unlike the same dive's JSON export (Header.Device.SerialNumber),
        // the FIT file_id message's serial_number field is simply absent. FitReaderService's
        // getComputer() falls back to a manufacturer+product identifier for exactly this case -
        // without that fallback, every Suunto FIT import would throw IllegalStateException
        // ("Expected to save computers, but this failed.").
        final var fileId = decode().getFileIdMesgs().getFirst();
        assertThat(fileId.getSerialNumber()).isNull();
        assertThat(fileId.getManufacturer()).isEqualTo(Manufacturer.SUUNTO);
        assertThat(fileId.getProductName()).isEqualTo("Suunto EON Core");
    }

    @Test
    void suuntoFitExportHasNoGpsPosition() throws Exception {
        final var session = decode().getSessionMesgs().getFirst();
        assertThat(session.getStartPositionLat()).isNull();
        assertThat(session.getStartPositionLong()).isNull();
    }

    @Test
    void suuntoFitRecordsCarryOnlyDepthAndTemperatureNoRichTelemetry() throws Exception {
        // Confirmed empirically: unlike Garmin's FIT export, per-record NDL/N2-load/CNS-load/
        // RMV-SAC fields are simply never populated. FitReaderService.getDiveProfile() already
        // handles every one of these as an Optional that defaults to null when absent, so this
        // needed no code change - documenting it here as the reason Suunto FIT profiles end up
        // with markedly less per-sample detail than the same dive's JSON export.
        final var first = decode().getRecordMesgs().getFirst();
        assertThat(first.getField(RecordMesg.DepthFieldNum)).isNotNull();
        assertThat(first.getTemperature()).isNotNull();
        assertThat(first.getNdlTime()).isNull();
        assertThat(first.getN2Load()).isNull();
        assertThat(first.getCnsLoad()).isNull();
        assertThat(first.getVolumeSac()).isNull();
        assertThat(first.getRmv()).isNull();
        assertThat(first.getPressureSac()).isNull();
    }

    @Test
    void suuntoFitExportHasNoGasSwitchEventsEvenThoughTwoGasesAreConfigured() throws Exception {
        // Two dive_gas messages exist (air + EAN50, matching the JSON export's Diving.Gases), but
        // no event message ever reports a mid-dive switch between them - only TIMER start/stop
        // events exist. This dive happens to use gas index 0 (air) throughout regardless (confirmed
        // against the JSON export's own single GasSwitch event, which switches onto gas 1 - the
        // same gas - at the very first sample), so this doesn't produce a wrong result here, but a
        // dive that genuinely switched gas mid-profile would lose that information entirely when
        // imported from this format's FIT export instead of its JSON export. Worth knowing as a
        // real information-loss gap in Suunto's own FIT export, not something
        // FitReaderService.getDiveProfile() can recover - it already handles zero gas-switch events
        // gracefully (stays on gas index 0), so no code change was needed here either.
        final var messages = decode();
        assertThat(messages.getDiveGasMesgs()).hasSize(2);
        final var hasGasSwitchEvent =
                messages.getEventMesgs().stream()
                        .anyMatch(e -> e.getEvent() == Event.DIVE_GAS_SWITCHED);
        assertThat(hasGasSwitchEvent).isFalse();
    }

    private static FitMessages decode() throws Exception {
        try (final InputStream in =
                Objects.requireNonNull(
                        SuuntoFitCharacterizationTest.class
                                .getClassLoader()
                                .getResourceAsStream("suunto-eon-core-dive-1-deco.fit"),
                        "fixture not found on classpath: suunto-eon-core-dive-1-deco.fit")) {
            return new FitDecoder().decode(in);
        }
    }
}
