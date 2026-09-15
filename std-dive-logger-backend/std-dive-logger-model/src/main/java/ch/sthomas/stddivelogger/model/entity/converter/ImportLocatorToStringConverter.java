package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.model.importfile.ImportLocator;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;

@Converter
public class ImportLocatorToStringConverter extends ObjectMapperToStringConverter<ImportLocator> {
    public ImportLocatorToStringConverter() {
        super(new TypeReference<>() {});
    }
}
