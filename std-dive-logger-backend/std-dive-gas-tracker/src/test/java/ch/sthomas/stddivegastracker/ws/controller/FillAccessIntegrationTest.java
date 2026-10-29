package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.fill.FillStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.support.AbstractFillIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** DOMAIN §10 for every fills endpoint, and the 400s of API.md §4 inputs. */
final class FillAccessIntegrationTest extends AbstractFillIntegrationTest {

    private record Call(HttpMethod method, String pathAfterFills, @Nullable Object body) {}

    @Test
    void everyEndpointIsNotFoundForNonMembersLeftMembersAndUnknownTeams() {
        final var room = room();
        final var leaver = addActiveMember(room.adminEmail(), room.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + room.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var fill =
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
        final var body = trimixBody(room, room.adminMemberId());
        final var calls =
                List.of(
                        new Call(HttpMethod.GET, "", null),
                        new Call(HttpMethod.POST, "/preview", body),
                        new Call(HttpMethod.POST, "", body),
                        new Call(HttpMethod.GET, "/" + fill.fillId(), null),
                        new Call(HttpMethod.PUT, "/" + fill.fillId(), body),
                        new Call(
                                HttpMethod.POST,
                                "/" + fill.fillId() + "/void",
                                Map.of("reason", "x")));

        for (final var caller : List.of(uniqueEmail("stranger"), emailOf(leaver))) {
            for (final var call : calls) {
                fillRequest(
                                call.method(),
                                caller,
                                room.teamId(),
                                call.pathAfterFills(),
                                call.body())
                        .expectStatus()
                        .isNotFound();
            }
        }
        for (final var call : calls) {
            send(
                            call.method(),
                            "/v1/teams/999999999/fills" + call.pathAfterFills(),
                            room.adminEmail(),
                            call.body())
                    .expectStatus()
                    .isNotFound();
        }
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId()).status())
                .isEqualTo(FillStatus.ACTIVE);
        assertThat(listFills(room.adminEmail(), room.teamId(), "").totalElements()).isEqualTo(1);
    }

    @Test
    void aFillOfAnotherTeamIsNotFoundThroughThisTeamsPath() {
        final var room = room();
        final var other = room();
        final var foreign =
                createFill(
                        other.adminEmail(),
                        other.teamId(),
                        trimixBody(other, other.adminMemberId()));
        final var path = "/" + foreign.fillId();

        for (final var call :
                List.of(
                        new Call(HttpMethod.GET, path, null),
                        new Call(HttpMethod.PUT, path, trimixBody(room, room.adminMemberId())),
                        new Call(HttpMethod.POST, path + "/void", Map.of("reason", "mine now")))) {
            fillRequest(
                            call.method(),
                            room.adminEmail(),
                            room.teamId(),
                            call.pathAfterFills(),
                            call.body())
                    .expectStatus()
                    .isNotFound();
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "?includeVoided=true").result())
                .isEmpty();
        assertThat(getFill(other.adminEmail(), other.teamId(), foreign.fillId()).status())
                .isEqualTo(FillStatus.ACTIVE);
    }

    @Test
    void anyActiveMemberRecordsAFillAndTheRecorderTheChargedMemberOrAnAdminMayChangeIt() {
        final var room = room();
        final var recorder = addActiveMember(room.adminEmail(), room.teamId());
        final var charged = addActiveMember(room.adminEmail(), room.teamId());
        final var other = addActiveMember(room.adminEmail(), room.teamId());
        final var body = trimixBody(room, charged.memberId());
        final var team = room.teamId();

        // a plain member records a fill charged to another member
        final var fill = createFill(emailOf(recorder), team, body);
        assertThat(fill.recordedBy().memberId()).isEqualTo(recorder.memberId());
        assertThat(fill.chargedMember()).isNotNull();

        // an unrelated member may read it, but neither edit nor void it
        assertThat(getFill(emailOf(other), team, fill.fillId())).isEqualTo(fill);
        fillRequest(HttpMethod.PUT, emailOf(other), team, "/" + fill.fillId(), body)
                .expectStatus()
                .isForbidden();
        fillRequest(
                        HttpMethod.POST,
                        emailOf(other),
                        team,
                        "/" + fill.fillId() + "/void",
                        Map.of("reason", "no"))
                .expectStatus()
                .isForbidden();
        assertThat(getFill(room.adminEmail(), team, fill.fillId())).isEqualTo(fill);

        // the recorder, the charged member and an admin may edit ...
        for (final var editor : List.of(emailOf(recorder), emailOf(charged), room.adminEmail())) {
            final var edited =
                    updateFill(editor, team, fill.fillId(), with(body, "notes", "by " + editor));
            assertThat(edited.recordedBy().memberId()).isEqualTo(recorder.memberId());
            assertThat(edited.notes()).isEqualTo("by " + editor);
        }
        // ... and void (a new fill each time)
        for (final var voider : List.of(emailOf(recorder), emailOf(charged), room.adminEmail())) {
            final var other2 = createFill(emailOf(recorder), team, body);
            final var voided = voidFill(voider, team, other2.fillId(), "by " + voider);
            assertThat(voided.status()).isEqualTo(FillStatus.VOIDED);
        }
        // a member charged on a fill is no admin: they cannot touch someone else's fill
        final var others = createFill(emailOf(other), team, trimixBody(room, other.memberId()));
        fillRequest(HttpMethod.PUT, emailOf(charged), team, "/" + others.fillId(), body)
                .expectStatus()
                .isForbidden();
        // moving the charge to someone else is the editor's call (any ACTIVE member can be charged)
        final var moved =
                updateFill(
                        emailOf(other),
                        team,
                        others.fillId(),
                        trimixBody(room, charged.memberId()));
        assertThat(moved.chargedMember()).isNotNull();
        assertThat(Objects.requireNonNull(moved.chargedMember()).memberId())
                .isEqualTo(charged.memberId());
        // ... and then the new charged member may edit, while a bystander still may not void
        fillRequest(HttpMethod.PUT, emailOf(charged), team, "/" + others.fillId(), body)
                .expectStatus()
                .isOk();
        fillRequest(
                        HttpMethod.POST,
                        emailOf(recorder),
                        team,
                        "/" + others.fillId() + "/void",
                        Map.of("reason", "x"))
                .expectStatus()
                .isForbidden();
    }

    @Test
    void invalidFillsAreRejectedAndStoreNothing() {
        final var room = room();
        final var diver = addActiveMember(room.adminEmail(), room.teamId());
        final var leaver = addActiveMember(room.adminEmail(), room.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + room.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var other = room();
        final var he = room.he().vesselId();
        final var o2 = room.o2().vesselId();
        final var good = fromBank(he, 70);

        final var bodies = new ArrayList<Map<String, Object>>();
        // stages
        bodies.add(withStages(cylinder(12, diver.memberId()), 0));
        bodies.add(without(fillBody(cylinder(12, diver.memberId()), 0, good), "stages"));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, fromBank(he, 70), fromBank(o2, 70)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, fromBank(he, 70), fromBank(o2, 60)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 10, fromBank(he, 10)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 20, fromBank(he, 15)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, fromBank(he, 401)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, fromBank(he, -1)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, without(good, "pressureAfterBar")));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, withGauge(good, 401)));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, without(good, "source")));
        bodies.add(
                fillBody(
                        cylinder(12, diver.memberId()),
                        0,
                        with(good, "source", Map.of("type", "TANK"))));
        // start
        bodies.add(fillBody(cylinder(12, diver.memberId()), 401, good));
        bodies.add(without(fillBody(cylinder(12, diver.memberId()), 0, good), "startPressureBar"));
        bodies.add(
                with(
                        fillBody(cylinder(12, diver.memberId()), 0, good),
                        "startMix",
                        Map.of("o2", 0.8, "he", 0.5)));
        bodies.add(
                with(
                        fillBody(cylinder(12, diver.memberId()), 0, good),
                        "endAnalysis",
                        Map.of("o2", 1.5, "he", 0)));
        bodies.add(
                with(fillBody(cylinder(12, diver.memberId()), 0, good), "notes", "n".repeat(1001)));
        // target
        bodies.add(without(fillBody(cylinder(12, diver.memberId()), 0, good), "target"));
        bodies.add(fillBody(Map.of("type", "SPACESHIP"), 0, good));
        bodies.add(fillBody(Map.of("waterVolumeLitres", 12), 0, good));
        bodies.add(fillBody(cylinder(0, diver.memberId()), 0, good));
        bodies.add(fillBody(cylinder(5000.1, diver.memberId()), 0, good));
        bodies.add(
                fillBody(
                        with(cylinder(12, diver.memberId()), "cylinderLabel", "x".repeat(61)),
                        0,
                        good));
        // ids that are not usable: unknown, another team's, LEFT, absent
        bodies.add(fillBody(cylinder(12, 999_999_999L), 0, good));
        bodies.add(fillBody(cylinder(12, other.adminMemberId()), 0, good));
        bodies.add(fillBody(cylinder(12, leaver.memberId()), 0, good));
        bodies.add(fillBody(Map.of("type", "CYLINDER", "waterVolumeLitres", 12), 0, good));
        bodies.add(fillBody(cylinder(12, diver.memberId()), 0, fromBank(999_999_999L, 70)));
        bodies.add(
                fillBody(cylinder(12, diver.memberId()), 0, fromBank(other.he().vesselId(), 70)));
        bodies.add(fillBody(bankTarget(other.o2().vesselId()), 0, good));
        bodies.add(fillBody(bankTarget(999_999_999L), 0, good));
        bodies.add(fillBody(Map.of("type", "VESSEL"), 0, good));

        for (final var body : bodies) {
            fillRequest(HttpMethod.POST, room.adminEmail(), room.teamId(), "", body)
                    .expectStatus()
                    .isBadRequest();
            fillRequest(HttpMethod.POST, room.adminEmail(), room.teamId(), "/preview", body)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "?includeVoided=true").result())
                .isEmpty();
        assertThat(count("SELECT count(*) FROM t_fill WHERE fk_team_id = ?", room.teamId()))
                .isZero();

        // the limits themselves, and a PENDING member (no user yet), are fine
        final var pending =
                addMember(room.adminEmail(), room.teamId(), uniqueEmail("pending"), Role.MEMBER);
        createFill(
                room.adminEmail(),
                room.teamId(),
                fillBody(
                        with(cylinder(5000, pending.memberId()), "cylinderLabel", "x".repeat(60)),
                        0,
                        fromBank(he, 1)));
    }

    @Test
    void aFillHasAtMostTenStages() {
        final var room = room();
        final var stages = new ArrayList<Map<String, Object>>();
        for (int bar = 10; bar <= 110; bar += 10) {
            stages.add(fromBank(room.o2().vesselId(), bar));
        }
        assertThat(stages).hasSize(11);
        final var tooMany = fillBody(cylinder(12, room.adminMemberId()), 0);
        tooMany.put("stages", stages);
        final var ten = fillBody(cylinder(12, room.adminMemberId()), 0);
        ten.put("stages", stages.subList(0, 10));

        for (final var path : List.of("", "/preview")) {
            assertThat(
                            Objects.requireNonNull(
                                    fillRequest(
                                                    HttpMethod.POST,
                                                    room.adminEmail(),
                                                    room.teamId(),
                                                    path,
                                                    tooMany)
                                            .expectStatus()
                                            .isBadRequest()
                                            .expectBody(String.class)
                                            .returnResult()
                                            .getResponseBody()))
                    .contains("A fill has at most 10 stages.");
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "").totalElements()).isZero();
        assertThat(createFill(room.adminEmail(), room.teamId(), ten).stages()).hasSize(10);
    }

    @Test
    void anUpdateIsValidatedLikeACreate() {
        final var room = room();
        final var fill =
                createFill(
                        room.adminEmail(), room.teamId(), trimixBody(room, room.adminMemberId()));
        final var path = "/" + fill.fillId();
        for (final var body :
                List.of(
                        fillBody(
                                cylinder(12, room.adminMemberId()),
                                0,
                                fromBank(room.he().vesselId(), 70),
                                fromBank(room.o2().vesselId(), 60)),
                        fillBody(cylinder(12, 999_999_999L), 0, fromBank(room.he().vesselId(), 70)),
                        fillBody(
                                cylinder(12, room.adminMemberId()),
                                0,
                                fromBank(999_999_999L, 70)))) {
            fillRequest(HttpMethod.PUT, room.adminEmail(), room.teamId(), path, body)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(getFill(room.adminEmail(), room.teamId(), fill.fillId())).isEqualTo(fill);
    }

    @Test
    void thePageParametersAreChecked() {
        final var room = room();
        for (final var query :
                List.of(
                        "?size=201",
                        "?size=0",
                        "?size=-1",
                        "?page=-1",
                        "?size=abc",
                        "?includeVoided=maybe",
                        "?memberId=x")) {
            fillRequest(HttpMethod.GET, room.adminEmail(), room.teamId(), query, null)
                    .expectStatus()
                    .isBadRequest();
        }
        assertThat(listFills(room.adminEmail(), room.teamId(), "?size=200&page=0").size())
                .isEqualTo(200);
    }

    // --- helpers ------------------------------------------------------------------------

    private static Map<String, Object> withStages(
            final Map<String, Object> target, final double startPressureBar) {
        final var body = new HashMap<String, Object>();
        body.put("target", target);
        body.put("startPressureBar", startPressureBar);
        body.put("stages", List.of());
        return body;
    }

    private static Map<String, Object> without(final Map<String, Object> body, final String key) {
        final var copy = new HashMap<>(body);
        copy.remove(key);
        return copy;
    }
}
