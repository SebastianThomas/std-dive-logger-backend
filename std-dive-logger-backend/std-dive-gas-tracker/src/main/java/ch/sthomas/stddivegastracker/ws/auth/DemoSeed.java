package ch.sthomas.stddivegastracker.ws.auth;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.fill.FillInput;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StageSource;
import ch.sthomas.stddivegastracker.model.payment.PaymentInput;
import ch.sthomas.stddivegastracker.model.team.AddMemberRequest;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamInput;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.user.UpdateMeRequest;
import ch.sthomas.stddivegastracker.model.vessel.CloseVesselRequest;
import ch.sthomas.stddivegastracker.model.vessel.CostLineInput;
import ch.sthomas.stddivegastracker.model.vessel.ReadingInput;
import ch.sthomas.stddivegastracker.model.vessel.VesselCreate;
import ch.sthomas.stddivegastracker.service.FillService;
import ch.sthomas.stddivegastracker.service.PaymentService;
import ch.sthomas.stddivegastracker.service.TeamService;
import ch.sthomas.stddivegastracker.service.UserService;
import ch.sthomas.stddivegastracker.service.VesselService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * LOCAL DEVELOPMENT ONLY (profile {@code demo-seed}): a "Demo Team" with three dev-login users,
 * four banks (one closed, one blended in-house), fills, a reading and payments - written through
 * the real services, in one transaction, once. Log in with dev login as {@code
 * demo-admin@example.test}, {@code alice@example.test} or {@code bob@example.test}.
 */
@Profile("demo-seed")
@Component
public final class DemoSeed {

    static final String TEAM_NAME = "Demo Team";
    static final String ADMIN_EMAIL = "demo-admin@example.test";
    private static final String ALICE_EMAIL = "alice@example.test";
    private static final String BOB_EMAIL = "bob@example.test";
    private static final GasMix EAN32 = new GasMix(0.32, 0);

    private static final Logger logger = LoggerFactory.getLogger(DemoSeed.class);

    private final UserService userService;
    private final TeamService teamService;
    private final VesselService vesselService;
    private final FillService fillService;
    private final PaymentService paymentService;
    private final TransactionTemplate transaction;

    public DemoSeed(
            final UserService userService,
            final TeamService teamService,
            final VesselService vesselService,
            final FillService fillService,
            final PaymentService paymentService,
            final TransactionTemplate transaction) {
        this.userService = userService;
        this.teamService = teamService;
        this.vesselService = vesselService;
        this.fillService = fillService;
        this.paymentService = paymentService;
        this.transaction = transaction;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        transaction.executeWithoutResult(_ -> seed(Instant.now().truncatedTo(ChronoUnit.MINUTES)));
    }

    /** Idempotent: does nothing when the demo admin already has the demo team. */
    void seed(final Instant now) {
        final var admin = user(ADMIN_EMAIL, "Dana (admin)");
        final var alice = user(ALICE_EMAIL, "Alice");
        final var bob = user(BOB_EMAIL, "Bob");
        if (userService.me(admin).teams().stream()
                .anyMatch(team -> TEAM_NAME.equals(team.name()))) {
            return;
        }

        final var team =
                teamService.create(
                        admin,
                        new TeamInput(TEAM_NAME, "CHF", LossPolicy.TRUE_UP, 0.05, 20, null, null));
        final var teamId = team.teamId();
        final var adminId = team.memberId();
        final var aliceId =
                teamService
                        .addMember(
                                teamId,
                                admin,
                                new AddMemberRequest(usernameOf(ALICE_EMAIL), Role.MEMBER))
                        .memberId();
        final var bobId =
                teamService
                        .addMember(
                                teamId,
                                admin,
                                new AddMemberRequest(usernameOf(BOB_EMAIL), Role.MEMBER))
                        .memberId();

        // the nearly empty bank the team already had when it started using the app
        final var oldOxygen =
                bank(admin, teamId, "O2 bank #1", GasMix.OXYGEN, 60, ago(now, 70), bobId, "120");
        final var helium =
                bank(admin, teamId, "He bank", GasMix.HELIUM, 200, ago(now, 40), adminId, "800");
        final var oxygen =
                bank(admin, teamId, "O2 bank #2", GasMix.OXYGEN, 200, ago(now, 30), aliceId, "300");
        final var nitrox = bank(admin, teamId, "EAN32 bank", GasMix.AIR, 0, ago(now, 25), 0, null);

        // nitrox by partial pressure from the old O2 bank, which then goes back to the supplier
        final var nitroxDivers = List.of(aliceId, bobId, adminId, aliceId);
        for (var i = 0; i < nitroxDivers.size(); i++) {
            cylinderFill(
                    admin,
                    teamId,
                    ago(now, 65 - 8 * i),
                    12,
                    nitroxDivers.get(i),
                    List.of(stage(oldOxygen, 28 + i), air(200)));
        }
        vesselService.close(teamId, oldOxygen, admin, new CloseVesselRequest(30.0, ago(now, 31)));

        // blend the in-house EAN32 bank: O2 first, then topped with air, analysed afterwards
        fillService.create(
                teamId,
                admin,
                new FillInput(
                        ago(now, 24),
                        new FillTarget.Vessel(nitrox, null),
                        0.0,
                        null,
                        List.of(stage(oxygen, 26), air(200)),
                        EAN32,
                        "Blended the house EAN32"));

        // trimix 21/35-ish: He, then O2, then air - twinsets and singles
        record Trimix(long memberId, double waterVolumeLitres, int daysAgo) {}
        for (final var trimix :
                List.of(
                        new Trimix(aliceId, 24, 20),
                        new Trimix(bobId, 12, 16),
                        new Trimix(adminId, 24, 12),
                        new Trimix(aliceId, 24, 6),
                        new Trimix(bobId, 12, 2))) {
            cylinderFill(
                    alice,
                    teamId,
                    ago(now, trimix.daysAgo()),
                    trimix.waterVolumeLitres(),
                    trimix.memberId(),
                    List.of(stage(helium, 70), stage(oxygen, 92), air(200)));
        }

        // decanting EAN32 deco/stage cylinders from the house bank
        final var decanters = List.of(bobId, aliceId, adminId);
        for (var i = 0; i < decanters.size(); i++) {
            cylinderFill(
                    bob,
                    teamId,
                    ago(now, 15 - 5 * i),
                    11.1,
                    decanters.get(i),
                    List.of(stage(nitrox, 150)));
        }

        vesselService.addReading(teamId, helium, admin, new ReadingInput(150.0, ago(now, 7)));
        payment(admin, teamId, bobId, adminId, "50.00", ago(now, 10), "TWINT");
        payment(admin, teamId, aliceId, adminId, "20.00", ago(now, 5), "Cash");
        logger.warn(
                "demo-seed: created '{}' for {} (local development only).", TEAM_NAME, ADMIN_EMAIL);
    }

    private static String usernameOf(final String email) {
        return email.substring(0, email.indexOf('@'));
    }

    private GasUser user(final String email, final String displayName) {
        final var user =
                userService.resolveAuthenticated(
                        DevLoginController.devUserId(email), email, usernameOf(email));
        userService.updateMe(user, new UpdateMeRequest(displayName));
        return user;
    }

    private long bank(
            final GasUser admin,
            final long teamId,
            final String label,
            final GasMix mix,
            final double initialPressureBar,
            final Instant openedAt,
            final long payerMemberId,
            final @Nullable String cost) {
        final var costLine =
                cost == null
                        ? null
                        : new CostLineInput(
                                payerMemberId,
                                new BigDecimal(cost),
                                openedAt,
                                "Bought " + label.toLowerCase(Locale.ROOT));
        return vesselService
                .create(
                        teamId,
                        admin,
                        new VesselCreate(
                                label,
                                50,
                                mix,
                                initialPressureBar,
                                null,
                                null,
                                openedAt,
                                null,
                                costLine == null ? List.of() : List.of(costLine)))
                .vesselId();
    }

    private void cylinderFill(
            final GasUser recordedBy,
            final long teamId,
            final Instant filledAt,
            final double waterVolumeLitres,
            final long chargedMemberId,
            final List<FillInput.Stage> stages) {
        fillService.create(
                teamId,
                recordedBy,
                new FillInput(
                        filledAt,
                        new FillTarget.Cylinder(waterVolumeLitres, null, chargedMemberId),
                        0.0,
                        null,
                        stages,
                        null,
                        null));
    }

    private void payment(
            final GasUser admin,
            final long teamId,
            final long fromMemberId,
            final long toMemberId,
            final String amount,
            final Instant paidAt,
            final String note) {
        paymentService.record(
                teamId,
                admin,
                new PaymentInput(fromMemberId, toMemberId, new BigDecimal(amount), paidAt, note));
    }

    private static FillInput.Stage stage(final long vesselId, final double pressureAfterBar) {
        return new FillInput.Stage(new StageSource.Vessel(vesselId, null), pressureAfterBar, null);
    }

    private static FillInput.Stage air(final double pressureAfterBar) {
        return new FillInput.Stage(new StageSource.Air(), pressureAfterBar, null);
    }

    private static Instant ago(final Instant now, final int days) {
        return now.minus(Duration.ofDays(days));
    }
}
