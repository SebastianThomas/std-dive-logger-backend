package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.entity.DiveProfileHistoryEntity.TrimCut;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

import java.util.List;

@Converter
public class TrimCutsToStringConverter extends ObjectMapperToStringConverter<List<TrimCut>> {
    public TrimCutsToStringConverter() {
        super(new TypeReference<>() {});
    }
}
