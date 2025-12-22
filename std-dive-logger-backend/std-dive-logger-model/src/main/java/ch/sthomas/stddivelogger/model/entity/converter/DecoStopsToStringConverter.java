package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;

import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.persistence.Converter;

import java.util.List;

@Converter(autoApply = true)
public class DecoStopsToStringConverter extends ObjectMapperToStringConverter<List<DecoStop>> {
    public DecoStopsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
