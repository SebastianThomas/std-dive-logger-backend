package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

@Converter
public class DecoSettingsToStringConverter extends ObjectMapperToStringConverter<DecoSettings> {
    public DecoSettingsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
