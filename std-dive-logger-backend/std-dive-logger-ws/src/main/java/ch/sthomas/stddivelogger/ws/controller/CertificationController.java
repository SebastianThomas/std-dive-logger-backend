package ch.sthomas.stddivelogger.ws.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ch.sthomas.stddivelogger.model.controller.CertificationBody;
import ch.sthomas.stddivelogger.model.controller.CreateCertificationAgencyBody;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.Certification;
import ch.sthomas.stddivelogger.model.user.CertificationAgency;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.CertificationService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/certifications")
@Validated
public class CertificationController {

    private final CertificationService certificationService;

    public CertificationController(final CertificationService certificationService) {
        this.certificationService = certificationService;
    }

    @Operation(
            summary =
                    "List certifying agencies (TDI, SSI, ...), optionally filtered by ?query=. "
                            + "Search this before ever creating a new one. Ranked by how many "
                            + "certifications the current user already holds with each agency, "
                            + "ties broken alphabetically.")
    @GetMapping("/agencies")
    public List<CertificationAgency> getAgencies(
            @AuthenticationPrincipal final @Nullable User user,
            @RequestParam(required = false, defaultValue = "") final String query) {
        return certificationService.getAgencies(user, query);
    }

    @Operation(
            summary =
                    "Add a new certifying agency - only use this once a thorough search of the "
                            + "existing list (GET /agencies) has confirmed it's genuinely missing. "
                            + "Requires a full name and website URL (not just a short code) and "
                            + "rejects an exact case-insensitive name match, to raise the bar "
                            + "against duplicate/mistyped/troll entries.")
    @PostMapping(path = "/agencies", consumes = APPLICATION_JSON_VALUE)
    public CertificationAgency createAgency(
            @AuthenticationPrincipal final @Nullable User user,
            @NotNull @Valid @RequestBody final CreateCertificationAgencyBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to add a certifying agency.");
        }
        return certificationService.createAgency(body);
    }

    @Operation(summary = "List the current user's own certifications, most recent first")
    @GetMapping
    public List<Certification> getCertifications(
            @AuthenticationPrincipal final @Nullable User user) {
        if (user == null) {
            throw new UnauthorizedException("Log in to view certifications.");
        }
        return certificationService.getCertificationsForUser(user);
    }

    @Operation(summary = "Create a new certification for the current user")
    @PostMapping
    public Certification createCertification(
            @AuthenticationPrincipal final @Nullable User user,
            @NotNull @Valid @RequestBody final CertificationBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to create a certification.");
        }
        return certificationService.createCertification(user, body);
    }

    @Operation(summary = "Update a certification owned by the current user")
    @PutMapping("/{id}")
    public Certification updateCertification(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable @Positive final long id,
            @NotNull @Valid @RequestBody final CertificationBody body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to update a certification.");
        }
        return certificationService.updateCertification(user, id, body);
    }

    @Operation(summary = "Delete a certification owned by the current user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertification(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable @Positive final long id) {
        if (user == null) {
            throw new UnauthorizedException("Log in to delete a certification.");
        }
        certificationService.deleteCertification(user, id);
        return ResponseEntity.noContent().build();
    }
}
