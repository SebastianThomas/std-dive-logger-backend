package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

@Converter(autoApply = true)
public class PendingImportPayloadToStringConverter
        extends ObjectMapperToStringConverter<PendingImportPayload> {
    public PendingImportPayloadToStringConverter() {
        super(new TypeReference<>() {});
    }
}
