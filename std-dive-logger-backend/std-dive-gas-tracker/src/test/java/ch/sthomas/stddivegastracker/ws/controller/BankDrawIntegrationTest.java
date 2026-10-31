package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivegastracker.calc.gas.GasContent;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.calc.gas.VirialFit;
import ch.sthomas.stddivegastracker.model.fill.FillSummary;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StageSource;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** DOMAIN §5.7: a fill recorded from a bank's pressure drop, with no cylinder measured. */
final class BankDrawIntegrationTest extends AbstractFillIntegrationTest {

    private static final double HE_DRAW_200_TO_150 =
            GasContent.contentLitres(50, 200, GasMix.HELIUM, VirialFit.INSTANCE)
                    - GasContent.contentLitres(50, 150, GasMix.HELIUM, VirialFit.INSTANCE);

    @Test
    void theBanksDropIsTheDrawChargedToTheMemberAndItsGaugeTheNewLevel() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());

        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        bankDraw(diver.memberId(), room.he().vesselId(), 200, 150));

        assertThat(fill.target()).isEqualTo(new FillTarget.BankDraw(diver.memberId()));
        assertThat(fill.chargedMember())
                .isEqualTo(new MemberRef(diver.memberId(), diver.displayName()));
        assertThat(fill.startPressureBar()).isEqualTo(200);
        assertThat(fill.endPressureBar()).isEqualTo(150);
        assertThat(fill.startMix()).isEqualTo(GasMix.HELIUM);
        assertThat(fill.endMix()).isEqualTo(GasMix.HELIUM);
        assertThat(fill.warnings()).isEmpty();
        final var stage = fill.stages().getFirst();
        assertThat(fill.stages()).hasSize(1);
        assertThat(stage.source())
                .isEqualTo(new StageSource.Vessel(room.he().vesselId(), "He bank"));
        assertThat(stage.drawLitres()).isCloseTo(HE_DRAW_200_TO_150, within(1e-6));
        assertThat(stage.addedHeLitres()).isCloseTo(HE_DRAW_200_TO_150, within(1e-6));
        assertThat(stage.lineLossLitres()).isZero();
        assertThat(stage.sourcePressureAfterBar()).isEqualTo(150);
        assertThat(fill.totalCharge()).isGreaterThan(BigDecimal.ZERO);

        final var bank = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        assertThat(bank.estimatedPressureBar()).isCloseTo(150, within(1e-6));
        assertThat(bank.readings()).hasSize(1);

        assertThat(listFills(room.adminEmail(), room.teamId(), "").result())
                .extracting(FillSummary::targetLabel)
                .containsExactly("From He bank");
    }

    @Test
    void aPreviewEqualsWhatIsStoredAndAnEditReplacesTheReading() {
        final var room = room();
        final var body = bankDraw(room.adminMemberId(), room.he().vesselId(), 200, 150);

        final var preview = previewFill(room.adminEmail(), room.teamId(), body);
        final var fill = createFill(room.adminEmail(), room.teamId(), body);
        assertThat(preview.totalCharge()).isEqualByComparingTo(fill.totalCharge());

        final var edited =
                updateFill(
                        room.adminEmail(),
                        room.teamId(),
                        fill.fillId(),
                        bankDraw(room.adminMemberId(), room.he().vesselId(), 200, 120));
        assertThat(edited.endPressureBar()).isEqualTo(120);
        assertThat(edited.stages().getFirst().drawLitres())
                .isGreaterThan(fill.stages().getFirst().drawLitres());

        final var bank = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        assertThat(bank.readings()).hasSize(1);
        assertThat(bank.estimatedPressureBar()).isCloseTo(120, within(1e-6));
    }

    @Test
    void voidingGivesTheGasBackAndRemovesTheReading() {
        final var room = room();
        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        bankDraw(room.adminMemberId(), room.he().vesselId(), 200, 150));

        voidFill(room.adminEmail(), room.teamId(), fill.fillId(), "Wrong bank");

        final var bank = getVessel(room.adminEmail(), room.teamId(), room.he().vesselId());
        assertThat(bank.readings()).isEmpty();
        assertThat(bank.estimatedPressureBar()).isCloseTo(200, within(1e-6));
    }

    @Test
    void theChargedMemberMayChangeItLikeACylinderFill() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var fill =
                createFill(
                        room.adminEmail(),
                        room.teamId(),
                        bankDraw(diver.memberId(), room.he().vesselId(), 200, 150));

        final var voided =
                voidFill(emailOf(diver), room.teamId(), fill.fillId(), "Took it from the O2 bank");

        assertThat(voided.voidReason()).isEqualTo("Took it from the O2 bank");
    }

    @Test
    void aBankDrawNeedsOneBankWhosePressureWentDownAndAMemberOfTheTeam() {
        final var room = room();
        final var member = room.adminMemberId();
        final var he = room.he().vesselId();
        final var rising = bankDraw(member, he, 150, 200);
        final var unchanged = bankDraw(member, he, 150, 150);
        final var unknownMember = bankDraw(999_999_999L, he, 200, 150);
        final var unknownBank = bankDraw(member, 999_999_999L, 200, 150);
        final var air = bankDraw(member, he, 200, 150);
        air.put("stages", List.of(fromAir(150)));
        final var twoBanks = bankDraw(member, he, 200, 150);
        twoBanks.put("stages", List.of(fromBank(he, 150), fromBank(room.o2().vesselId(), 100)));

        for (final var body :
                List.of(rising, unchanged, unknownMember, unknownBank, air, twoBanks)) {
            fillRequest(HttpMethod.POST, room.adminEmail(), room.teamId(), "", body)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "").result()).isEmpty();
    }

    private static Map<String, Object> bankDraw(
            final long memberId, final long vesselId, final double before, final double after) {
        final var body = new HashMap<String, Object>();
        body.put("filledAt", day(1).toEpochMilli());
        body.put("target", Map.of("type", "BANK_DRAW", "chargedMemberId", memberId));
        body.put("startPressureBar", before);
        body.put(
                "stages",
                List.of(
                        Map.of(
                                "source",
                                Map.of("type", "VESSEL", "vesselId", vesselId),
                                "pressureAfterBar",
                                after)));
        return body;
    }
}
