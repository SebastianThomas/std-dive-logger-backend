package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

import java.util.List;

@Converter(autoApply = true)
public class DecoStopsToStringConverter extends ObjectMapperToStringConverter<List<DecoStop>> {
    public DecoStopsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
