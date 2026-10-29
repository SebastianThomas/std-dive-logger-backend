package ch.sthomas.stddivegastracker.ws.controller;

import ch.sthomas.stddivegastracker.model.Page;
import ch.sthomas.stddivegastracker.model.fill.Fill;
import ch.sthomas.stddivegastracker.model.fill.FillInput;
import ch.sthomas.stddivegastracker.model.fill.FillPreview;
import ch.sthomas.stddivegastracker.model.fill.FillSummary;
import ch.sthomas.stddivegastracker.model.fill.VoidFillRequest;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.FillService;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API.md §4 "Fills". */
@RestController
@RequestMapping("/v1/teams/{teamId}/fills")
public final class FillController {

    private final FillService fillService;

    public FillController(final FillService fillService) {
        this.fillService = fillService;
    }

    @GetMapping
    public Page<FillSummary> list(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestParam(required = false) final @Nullable Long memberId,
            @RequestParam(required = false) final @Nullable Long vesselId,
            @RequestParam(defaultValue = "false") final boolean includeVoided,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "50") final int size) {
        return fillService.list(teamId, user, memberId, vesselId, includeVoided, page, size);
    }

    /** {@code replacing}: the fill being edited, priced as if replaced (not as an extra fill). */
    @PostMapping("/preview")
    public FillPreview preview(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestParam(required = false) final @Nullable Long replacing,
            @RequestBody final FillInput request) {
        return fillService.preview(teamId, user, replacing, request);
    }

    @PostMapping
    public Fill create(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @RequestBody final FillInput request) {
        return fillService.create(teamId, user, request);
    }

    @GetMapping("/{fillId}")
    public Fill get(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long fillId) {
        return fillService.get(teamId, fillId, user);
    }

    @PutMapping("/{fillId}")
    public Fill update(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long fillId,
            @RequestBody final FillInput request) {
        return fillService.update(teamId, fillId, user, request);
    }

    @PostMapping("/{fillId}/void")
    public Fill voidFill(
            @AuthenticationPrincipal final GasUser user,
            @PathVariable final long teamId,
            @PathVariable final long fillId,
            @RequestBody final VoidFillRequest request) {
        return fillService.voidFill(teamId, fillId, user, request);
    }
}
