package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftImportRequest(List<DivesoftDiveDetailResponse> dives) {}
