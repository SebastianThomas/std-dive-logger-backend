package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftImportRequest(@NotEmpty @Valid List<DivesoftDiveDetailResponse> dives) {}
