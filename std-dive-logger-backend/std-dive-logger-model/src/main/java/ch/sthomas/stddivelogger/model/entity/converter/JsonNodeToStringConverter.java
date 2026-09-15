package ch.sthomas.stddivelogger.model.entity.converter;

import jakarta.persistence.Converter;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

/** Free-form JSONB values (file metadata, imported / proposed field values). */
@Converter
public class JsonNodeToStringConverter extends ObjectMapperToStringConverter<JsonNode> {
    public JsonNodeToStringConverter() {
        super(new TypeReference<>() {});
    }
}
