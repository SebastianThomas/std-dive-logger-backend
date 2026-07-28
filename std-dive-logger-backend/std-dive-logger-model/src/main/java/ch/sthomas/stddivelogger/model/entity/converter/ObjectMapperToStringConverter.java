package ch.sthomas.stddivelogger.model.entity.converter;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import com.pivovarit.function.exception.WrappedException;

import jakarta.persistence.AttributeConverter;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("com.intellij.jpb.ConverterNotAnnotatedInspection")
public abstract class ObjectMapperToStringConverter<T> implements AttributeConverter<T, String> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JsonMapper objectMapper = ObjectMapperUtils.objectMapperBuilder(_ -> {}).build();

    private final TypeReference<T> typeReference;

    protected ObjectMapperToStringConverter(final TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public @Nullable String convertToDatabaseColumn(final @Nullable T attributes) {
        if (attributes == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (final JacksonException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public @Nullable T convertToEntityAttribute(final @Nullable String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, typeReference);
        } catch (final JacksonException e) {
            logger.info("Could not deserialize {} as {}.", dbData, typeReference, e);
            return null;
        }
    }
}
