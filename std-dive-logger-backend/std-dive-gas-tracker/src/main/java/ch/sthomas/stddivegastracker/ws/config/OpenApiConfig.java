package ch.sthomas.stddivegastracker.ws.config;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The OpenAPI document is the frontend's type source (task D8), so it states the wire format
 * exactly: instants are epoch millis, money is a decimal string (JacksonConfig), and a record
 * component is required unless it is {@code @Nullable}. Nested record names are set with
 * {@code @Schema(name = …)} where simple names would collide.
 */
@Configuration
public class OpenApiConfig {

    private static final String MODEL_PACKAGE = "ch.sthomas.stddivegastracker.model";
    // generic Page<T> schemas are named Page + T (e.g. PageFillSummary)
    private static final String PAGE_PREFIX = "Page";

    static {
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        Instant.class,
                        new IntegerSchema().format("int64").description("Epoch milliseconds"))
                .replaceWithSchema(
                        BigDecimal.class,
                        new StringSchema().format("decimal").description("Money, e.g. \"12.30\""));
    }

    @Bean
    public OpenAPI gasTrackerOpenApi() {
        return new OpenAPI().info(new Info().title("STD Dive Gas Tracker API").version("v1"));
    }

    /** {@code required} from the records: every component that is not {@code @Nullable}. */
    @Bean
    public OpenApiCustomizer requiredFromNullability() {
        final var records = modelRecordsBySchemaName();
        return openApi -> {
            final var components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) {
                return;
            }
            components
                    .getSchemas()
                    .forEach(
                            (name, schema) -> {
                                final var type =
                                        name.startsWith(PAGE_PREFIX) && !records.containsKey(name)
                                                ? records.get(PAGE_PREFIX)
                                                : records.get(name);
                                if (type != null) {
                                    markRequired(propertiesHolder(schema), type);
                                }
                            });
        };
    }

    /** A subtype's own properties sit in its {@code allOf} part next to the parent reference. */
    private static Schema<?> propertiesHolder(final Schema<?> schema) {
        final var allOf = schema.getAllOf();
        if (allOf == null) {
            return schema;
        }
        return allOf.stream()
                .filter(part -> part.getProperties() != null)
                .findFirst()
                .orElse(schema);
    }

    private static void markRequired(final Schema<?> schema, final Class<?> type) {
        final var properties = schema.getProperties();
        final var components = type.getRecordComponents();
        if (properties == null || components == null) {
            return;
        }
        final var required =
                Arrays.stream(components)
                        .filter(component -> !isNullable(component))
                        .map(RecordComponent::getName)
                        .filter(properties::containsKey)
                        .sorted()
                        .toList();
        schema.setRequired(required.isEmpty() ? null : required);
    }

    private static boolean isNullable(final RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class);
    }

    /** Every record under the model package (+ GasMix), by the schema name springdoc gives it. */
    private static Map<String, Class<?>> modelRecordsBySchemaName() {
        final var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        final var records = new HashMap<String, Class<?>>();
        for (final var candidate : scanner.findCandidateComponents(MODEL_PACKAGE)) {
            final var type =
                    ClassUtils.resolveClassName(
                            Objects.requireNonNull(candidate.getBeanClassName()),
                            OpenApiConfig.class.getClassLoader());
            records.put(schemaName(type), type);
        }
        // the one calc type on the wire; calc is not scanned (its MemberBalance would collide)
        records.put(schemaName(GasMix.class), GasMix.class);
        return Map.copyOf(records);
    }

    private static String schemaName(final Class<?> type) {
        final var annotation = type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return annotation == null || annotation.name().isEmpty()
                ? type.getSimpleName()
                : annotation.name();
    }
}
