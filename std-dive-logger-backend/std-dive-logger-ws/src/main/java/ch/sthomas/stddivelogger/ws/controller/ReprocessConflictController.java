package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.importfile.ReprocessConflict;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.reprocess.ImportReprocessService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Changes re-processing stored dive files would make to real data, waiting for the diver - listed
 * on the backfill page above the backfill items.
 */
@RestController
@RequestMapping("/v1/reprocess-conflicts")
@Validated
public class ReprocessConflictController {

    public record OpenCount(long open) {}

    private final ImportReprocessService importReprocessService;

    public ReprocessConflictController(final ImportReprocessService importReprocessService) {
        this.importReprocessService = importReprocessService;
    }

    @Operation(summary = "Open changes from re-processing stored dive files, oldest first")
    @GetMapping("")
    public List<ReprocessConflict> list(@AuthenticationPrincipal final @Nullable User user) {
        return importReprocessService.listOpen(requireUser(user));
    }

    @Operation(summary = "How many changes from re-processing are open")
    @GetMapping("/count")
    public OpenCount count(@AuthenticationPrincipal final @Nullable User user) {
        return new OpenCount(importReprocessService.countOpen(requireUser(user)));
    }

    @Operation(
            summary =
                    "Applies a change; refused when the dive changed since it was proposed."
                            + " Returns the changes still open.")
    @PostMapping("/{id}/apply")
    public List<ReprocessConflict> apply(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long id) {
        return importReprocessService.apply(requireUser(user), id);
    }

    @Operation(summary = "Keeps the dive as it is. Returns the changes still open.")
    @PostMapping("/{id}/keep")
    public List<ReprocessConflict> keep(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long id) {
        return importReprocessService.keep(requireUser(user), id);
    }

    private static User requireUser(final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to review re-processing changes.");
        }
        return user;
    }
}
