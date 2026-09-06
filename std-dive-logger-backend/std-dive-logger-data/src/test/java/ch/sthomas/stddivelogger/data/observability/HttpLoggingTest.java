package ch.sthomas.stddivelogger.data.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.servlet.LogbookFilter;

import java.nio.charset.StandardCharsets;

class HttpLoggingTest {
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final Logger logger = (Logger) RequestLogSink.LOGGER;
    private final Logbook logbook =
            Logbook.builder()
                    .strategy(new SafeBodyStrategy())
                    .pathFilter(path -> "/test/{id}")
                    .sink(new RequestLogSink())
                    .build();

    @BeforeEach
    void capture() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void shortJsonIsAllowlistedAndCredentialsNeverReachTheLog() throws Exception {
        final var request =
                request(
                        "application/json",
                        """
                {"page":2,"password":"secret","token":"secret","cookies":{"session":"secret"},"blob":"secret","nested":{"password":"secret"}}
                """);
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("Cookie", "session=secret");
        request.setQueryString("password=secret&access_token=secret");
        final var response = new MockHttpServletResponse();
        new LogbookFilter(logbook)
                .doFilter(
                        request,
                        response,
                        (req, res) -> {
                            assertThat(req.getInputStream().readAllBytes())
                                    .isEqualTo(request.getContentAsByteArray());
                            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
                            ((jakarta.servlet.http.HttpServletResponse) res)
                                    .addHeader("Set-Cookie", "session=secret");
                            res.getOutputStream()
                                    .write("secret response".getBytes(StandardCharsets.UTF_8));
                        });
        assertThat(response.getContentAsString()).isEqualTo("secret response");
        assertThat(events.list).hasSize(2);
        assertThat(
                        events.list.stream()
                                .flatMap(event -> event.getKeyValuePairs().stream())
                                .toList()
                                .toString())
                .contains("page=2", "status=\"201\"", "duration_ms=", "/test/{id}")
                .doesNotContain("secret", "password", "Cookie", "Authorization");
        assertThat(events.list.getFirst().getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
    }

    @Test
    void largeMultipartBinaryAndUnknownLengthBodiesAreNotReadByLogging() throws Exception {
        for (final var type :
                new String[] {
                    "application/json",
                    "multipart/form-data",
                    "application/octet-stream",
                    "text/event-stream"
                }) {
            final var request = request(type, "x".repeat(8192));
            final var response = new MockHttpServletResponse();
            new LogbookFilter(logbook)
                    .doFilter(
                            request,
                            response,
                            (req, res) -> {
                                assertThat(req.getInputStream().readAllBytes()).hasSize(8192);
                                res.getOutputStream().write(new byte[65536]);
                            });
            assertThat(response.getContentAsByteArray()).hasSize(65536);
        }
        final var unknown =
                new MockHttpServletRequest() {
                    @Override
                    public jakarta.servlet.ServletInputStream getInputStream() {
                        throw new AssertionError("Logging must not read an unknown-length stream");
                    }
                };
        unknown.setContentType("application/json");
        new LogbookFilter(logbook)
                .doFilter(unknown, new MockHttpServletResponse(), (req, res) -> {});
        assertThat(events.list).hasSize(10);
        assertThat(
                        events.list.stream()
                                .filter(
                                        e ->
                                                e.getFormattedMessage()
                                                        .equals("HTTP request received"))
                                .toList())
                .allSatisfy(
                        e ->
                                assertThat(e.getKeyValuePairs().toString())
                                        .contains("request_body=\"{}\""));
    }

    @Test
    void clientAndServerFailuresHaveDashboardVisibleLevels() throws Exception {
        for (final var status : new int[] {401, 403, 404, 500, 503}) {
            final var response = new MockHttpServletResponse();
            new LogbookFilter(logbook)
                    .doFilter(
                            new MockHttpServletRequest(),
                            response,
                            (req, res) ->
                                    ((jakarta.servlet.http.HttpServletResponse) res)
                                            .setStatus(status));
            assertThat(events.list.getLast().getLevel().levelStr)
                    .isEqualTo(status >= 500 ? "ERROR" : "WARN");
        }
    }

    @Test
    void malformedOrStringValuedFieldsAreOmitted() {
        assertThat(RequestLogSink.preview("{\"password\":\"secret\",")).isEmpty();
        assertThat(RequestLogSink.preview("{\"page\":\"secret\"}")).isEmpty();
    }

    @Test
    void routeTemplatesRemovePathCredentialsAndUnknownPaths() throws Exception {
        final var mapping =
                new org.springframework.web.servlet.mvc.method.annotation
                        .RequestMappingHandlerMapping();
        mapping.registerMapping(
                org.springframework.web.servlet.mvc.method.RequestMappingInfo.paths("/items/{id}")
                        .build(),
                this,
                getClass().getDeclaredMethod("endpoint"));
        final var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("mapping", mapping);
        final var filter =
                new HttpLoggingConfiguration()
                        .routePathFilter(
                                beans.getBeanProvider(
                                        org.springframework.web.servlet.mvc.method.annotation
                                                .RequestMappingHandlerMapping.class));
        assertThat(filter.filter("/items/secret")).isEqualTo("/items/{id}");
        assertThat(filter.filter("/unknown/secret")).isEqualTo("UNKNOWN");
    }

    void endpoint() {}

    private static MockHttpServletRequest request(final String type, final String body) {
        final var request = new MockHttpServletRequest("POST", "/test/secret");
        request.setContentType(type);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Content-Length", request.getContentLength());
        return request;
    }
}
