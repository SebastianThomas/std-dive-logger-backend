package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shearwater Cloud's own native per-dive XML export ("Source File" in the app) - root element
 * {@code <dive>}, plain child elements throughout, no attributes. Richer than the same dive's
 * UDDF/DL7 exports: this is the only one of the three with real per-sample TTS ({@code ttsMins}),
 * next-stop depth/time, and PPO2/CCR fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShearwaterXmlExport(ShearwaterDiveLog diveLog) {}
