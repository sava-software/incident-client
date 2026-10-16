package software.sava.incident.io.exceptions;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

final class IncidentIoRequestExceptionTests {

  private record StubResponse(int statusCode) implements HttpResponse<byte[]> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (_, _) -> true);
    }

    @Override
    public byte[] body() {
      return null;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return null;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_2;
    }
  }

  @Test
  void parseErrorEnvelope() {
    final var exception = IncidentIoRequestException.parse(new StubResponse(422), """
        {"type":"validation_error","status":422,"request_id":"req-1","unknown":{},
        "errors":[
          {"code":"missing_required_field","message":"name is required","source":{"field":"name"}},
          {"code":"invalid_value","message":"unknown severity"}
        ]}""".getBytes(UTF_8)
    );
    assertEquals("validation_error", exception.type());
    assertEquals(422, exception.errorCode());
    assertEquals("req-1", exception.requestId());
    assertEquals(List.of(
        "missing_required_field: name is required",
        "invalid_value: unknown severity"
    ), exception.errors());
    assertEquals(List.of(
        new IncidentIoRequestException.ErrorDetail("missing_required_field", "name is required", Map.of(),
            new IncidentIoRequestException.ErrorSource("name", null)),
        new IncidentIoRequestException.ErrorDetail("invalid_value", "unknown severity", Map.of(), null)
    ), exception.errorDetails());
    assertEquals("missing_required_field: name is required; invalid_value: unknown severity", exception.getMessage());
    assertFalse(exception.canBeRetried());
  }

  @Test
  void emptyErrorsFallBackToType() {
    final var exception = IncidentIoRequestException.parse(new StubResponse(401), """
        {"type":"authentication_error","status":401}""".getBytes(UTF_8)
    );
    assertEquals("authentication_error", exception.getMessage());
    assertEquals(List.of(), exception.errors());
    assertFalse(exception.canBeRetried());
  }

  @Test
  void retriableStatusCodes() {
    final var body = """
        {"type":"rate_limit_error","status":429}""".getBytes(UTF_8);
    assertTrue(IncidentIoRequestException.parse(new StubResponse(429), body).canBeRetried());
    assertTrue(IncidentIoRequestException.parse(new StubResponse(500), body).canBeRetried());
    assertTrue(IncidentIoRequestException.parse(new StubResponse(503), body).canBeRetried());
    assertTrue(IncidentIoRequestException.parse(null, body).canBeRetried());
    assertFalse(IncidentIoRequestException.parse(new StubResponse(400), body).canBeRetried());
  }

  @Test
  void errorsWithoutCodeOrMessage() {
    final var exception = IncidentIoRequestException.parse(new StubResponse(422), """
        {"type":"validation_error","status":422,"errors":[{"message":"just a message"},{"code":"just_a_code"},{}]}""".getBytes(UTF_8)
    );
    assertEquals(List.of("just a message", "just_a_code", "unknown error"), exception.errors());
    assertEquals("just a message; just_a_code; unknown error", exception.getMessage());
    assertEquals(List.of(
        new IncidentIoRequestException.ErrorDetail(null, "just a message", Map.of(), null),
        new IncidentIoRequestException.ErrorDetail("just_a_code", null, Map.of(), null),
        new IncidentIoRequestException.ErrorDetail(null, null, Map.of(), null)
    ), exception.errorDetails());
  }

  @Test
  void structuredErrorEnvelopePreservesDiagnosticsAndProviderNeutralMessages() {
    final var response = new StubResponse(429);
    final var exception = IncidentIoRequestException.parse(response, """
        {"type":"too_many_requests","status":429,"request_id":"req-diagnostics",
         "errors":[{"code":"rate_limit_reached","message":"Slow down",
           "metadata":{"key\\\"\\\\\\n":"value\\\"\\\\\\t","second":"preserved"},
           "source":{"field":"severity_id","pointer":"/severity_id"}}],
         "rate_limit":{"name":"api_key","limit":9223372036854775807,
           "remaining":4294967296,"retry_after":"verbatim retry hint"},
         "debug":{"message":"internal detail","stacktrace":["first.go:12","second.go:34"]}}
        """.getBytes(UTF_8));
    assertEquals(List.of(new IncidentIoRequestException.ErrorDetail(
        "rate_limit_reached", "Slow down",
        Map.of("key\"\\\n", "value\"\\\t", "second", "preserved"),
        new IncidentIoRequestException.ErrorSource("severity_id", "/severity_id")
    )), exception.errorDetails());
    assertEquals(new IncidentIoRequestException.RateLimit(
        "api_key", Long.MAX_VALUE, 4294967296L, "verbatim retry hint"), exception.rateLimit());
    assertEquals(new IncidentIoRequestException.DebugInfo(
        "internal detail", List.of("first.go:12", "second.go:34")), exception.debug());
    assertEquals(List.of("rate_limit_reached: Slow down"), exception.errors());
    assertEquals("rate_limit_reached: Slow down", exception.getMessage());
    assertEquals("too_many_requests", exception.type());
    assertEquals("req-diagnostics", exception.requestId());
    assertEquals(429, exception.errorCode());
    assertSame(response, exception.httpResponse());
    assertTrue(exception.canBeRetried());
  }

  @Test
  void unknownNestedFieldsDoNotConsumeFollowingKnownFields() {
    final var exception = IncidentIoRequestException.parse(new StubResponse(422), """
        {"errors":[{"unknown":{"array":[1,{"nested":true}]},
          "source":{"unknown":[{},[]],"pointer":"/name","field":"name"},
          "metadata":{"after":"unknown"},"message":"invalid","code":"validation"}],
         "rate_limit":{"unknown":{"nested":[]},"name":"client_ip","limit":100,
           "remaining":98,"retry_after":"2026-10-16T20:00:00Z"},
         "debug":{"unknown":[{"nested":[]}],"message":"debug message","stacktrace":["trace"]},
         "request_id":"following","status":422,"type":"validation_error"}
        """.getBytes(UTF_8));
    assertEquals(List.of(new IncidentIoRequestException.ErrorDetail(
        "validation", "invalid", Map.of("after", "unknown"),
        new IncidentIoRequestException.ErrorSource("name", "/name"))), exception.errorDetails());
    assertEquals(new IncidentIoRequestException.RateLimit(
        "client_ip", 100, 98, "2026-10-16T20:00:00Z"), exception.rateLimit());
    assertEquals(new IncidentIoRequestException.DebugInfo("debug message", List.of("trace")), exception.debug());
    assertEquals("following", exception.requestId());
    assertEquals(422, exception.errorCode());
    assertEquals("validation_error", exception.type());
  }

  @Test
  void optionalDiagnosticsCanBeMissingNullOrEmpty() {
    for (final var fields : List.of("", ",\"debug\":null,\"rate_limit\":null")) {
      final var exception = IncidentIoRequestException.parse(new StubResponse(400),
          ("{\"type\":\"invalid_request_error\",\"errors\":[]" + fields + "}").getBytes(UTF_8));
      assertNull(exception.debug());
      assertNull(exception.rateLimit());
      assertEquals(List.of(), exception.errorDetails());
      assertEquals("invalid_request_error", exception.getMessage());
    }
    for (final var detail : List.of("{}", "{\"metadata\":null,\"source\":null}", "{\"metadata\":{}}")) {
      final var exception = IncidentIoRequestException.parse(new StubResponse(400),
          ("{\"errors\":[" + detail + "]}").getBytes(UTF_8));
      assertEquals(List.of(new IncidentIoRequestException.ErrorDetail(null, null, Map.of(), null)),
          exception.errorDetails());
      assertThrows(UnsupportedOperationException.class,
          () -> exception.errorDetails().getFirst().metadata().put("new", "value"));
    }
    for (final var debug : List.of("{}", "{\"stacktrace\":null}", "{\"stacktrace\":[]}")) {
      final var exception = IncidentIoRequestException.parse(new StubResponse(400),
          ("{\"debug\":" + debug + "}").getBytes(UTF_8));
      assertEquals(new IncidentIoRequestException.DebugInfo(null, List.of()), exception.debug());
      assertThrows(UnsupportedOperationException.class, () -> exception.debug().stacktrace().add("trace"));
    }
  }

  @Test
  void omittedSourceMembersAreNull() {
    final var exception = IncidentIoRequestException.parse(new StubResponse(422), """
        {"errors":[
          {"code":"pointer_only","source":{"pointer":"/name"}},
          {"code":"empty_source","source":{}}
        ]}
        """.getBytes(UTF_8));
    assertEquals(new IncidentIoRequestException.ErrorSource(null, "/name"),
        exception.errorDetails().get(0).source());
    assertEquals(new IncidentIoRequestException.ErrorSource(null, null),
        exception.errorDetails().get(1).source());
  }

  @Test
  void diagnosticCollectionsAreImmutableAtEverySize() {
    for (int size = 0; size <= 2; ++size) {
      final var metadata = new HashMap<String, String>();
      final var stacktrace = new ArrayList<String>();
      final var errorsJson = new StringBuilder("{\"errors\":[");
      for (int i = 0; i < size; ++i) {
        metadata.put("key" + i, "value" + i);
        stacktrace.add("trace" + i);
        if (i > 0) {
          errorsJson.append(',');
        }
        errorsJson.append("{\"code\":\"code").append(i).append("\",\"message\":\"message").append(i).append("\"}");
      }
      final var detail = new IncidentIoRequestException.ErrorDetail("code", "message", metadata, null);
      final var debug = new IncidentIoRequestException.DebugInfo("debug", stacktrace);
      final var expectedMetadata = Map.copyOf(metadata);
      final var expectedStacktrace = List.copyOf(stacktrace);
      metadata.put("later", "mutation");
      stacktrace.add("later mutation");
      assertEquals(expectedMetadata, detail.metadata());
      assertEquals(expectedStacktrace, debug.stacktrace());
      assertThrows(UnsupportedOperationException.class, () -> detail.metadata().put("added", "value"));
      assertThrows(UnsupportedOperationException.class, () -> debug.stacktrace().add("added"));
      final var exception = IncidentIoRequestException.parse(new StubResponse(422),
          errorsJson.append("]}").toString().getBytes(UTF_8));
      assertEquals(size, exception.errorDetails().size());
      assertThrows(UnsupportedOperationException.class, () -> exception.errorDetails().add(detail));
      assertThrows(UnsupportedOperationException.class, () -> exception.errors().add("added"));
    }
    assertEquals(Map.of(), new IncidentIoRequestException.ErrorDetail(null, null, null, null).metadata());
    assertEquals(List.of(), new IncidentIoRequestException.DebugInfo(null, null).stacktrace());
  }

  @Test
  void malformedNestedDiagnosticsThrowParseException() {
    for (final var body : List.of(
        "{\"errors\":[{\"metadata\":[]}]}",
        "{\"errors\":[{\"metadata\":{\"key\":null}}]}",
        "{\"errors\":[{\"source\":[]}]}",
        "{\"rate_limit\":{\"limit\":\"not a number\"}}",
        "{\"debug\":{\"stacktrace\":[null]}}",
        "{\"debug\":{\"stacktrace\":[{}]}}")) {
      final var response = new StubResponse(500);
      final var thrown = assertThrows(IncidentIoParseException.class,
          () -> IncidentIoRequestException.parse(response, body.getBytes(UTF_8)), body);
      assertSame(response, thrown.httpResponse());
      assertNotNull(thrown.getCause());
    }
  }

  @Test
  void diagnosticRecordsRejectNullCollectionMembers() {
    final var nullKey = new HashMap<String, String>();
    nullKey.put(null, "value");
    assertThrows(NullPointerException.class,
        () -> new IncidentIoRequestException.ErrorDetail("code", "message", nullKey, null));

    final var nullValue = new HashMap<String, String>();
    nullValue.put("key", null);
    assertThrows(NullPointerException.class,
        () -> new IncidentIoRequestException.ErrorDetail("code", "message", nullValue, null));

    final var nullFrame = new ArrayList<String>();
    nullFrame.add(null);
    assertThrows(NullPointerException.class,
        () -> new IncidentIoRequestException.DebugInfo("message", nullFrame));
  }

  @Test
  void collidingMetadataKeysPreserveContentsAndDefensiveCopy() {
    final var metadata = new HashMap<String, String>();
    final var body = new StringBuilder("{\"errors\":[{\"metadata\":{");
    // Equal-length combinations of Aa and BB have the same String.hashCode.
    for (int i = 0; i < 256; ++i) {
      final var keyBuilder = new StringBuilder(16);
      for (int bit = 0; bit < 8; ++bit) {
        keyBuilder.append((i & (1 << bit)) == 0 ? "Aa" : "BB");
      }
      final var key = keyBuilder.toString();
      final var value = "value" + i;
      metadata.put(key, value);
      if (i > 0) {
        body.append(',');
      }
      body.append('"').append(key).append("\":\"").append(value).append('"');
    }
    body.append("}}]}");
    final var exception = IncidentIoRequestException.parse(new StubResponse(422), body.toString().getBytes(UTF_8));
    final var parsedMetadata = exception.errorDetails().getFirst().metadata();
    assertEquals(metadata, parsedMetadata);
    assertThrows(UnsupportedOperationException.class, () -> parsedMetadata.put("later", "value"));

    final var expected = new HashMap<>(metadata);
    final var detail = new IncidentIoRequestException.ErrorDetail("code", "message", metadata, null);
    metadata.clear();
    metadata.put("later", "mutation");
    assertEquals(expected, detail.metadata());
    assertThrows(UnsupportedOperationException.class, () -> detail.metadata().put("later", "value"));
  }

  @Test
  void malformedEnvelopeThrowsParseException() {
    final var thrown = assertThrows(IncidentIoParseException.class, () ->
        IncidentIoRequestException.parse(new StubResponse(500), "<html>gateway error</html>".getBytes(UTF_8)));
    assertTrue(thrown.canBeRetried());
    assertEquals(0, thrown.errorCode());
    assertEquals(List.of(), thrown.errors());
    assertNotNull(thrown.getCause());
  }

  @Test
  void parseFailureMessageNamesTheStatusCode() {
    final var garbage = "not-json".getBytes(UTF_8);
    final var withResponse = assertThrows(IncidentIoParseException.class, () ->
        IncidentIoRequestException.parse(new StubResponse(418), garbage));
    assertTrue(withResponse.getMessage().contains("418"), withResponse.getMessage());

    final var withoutResponse = assertThrows(IncidentIoParseException.class, () ->
        IncidentIoRequestException.parse(null, garbage));
    assertTrue(withoutResponse.getMessage().contains("-1"), withoutResponse.getMessage());
  }

  @Test
  void accessorsAndToString() {
    final var response = new StubResponse(422);
    final var exception = IncidentIoRequestException.parse(response, """
        {"type":"validation_error","status":422}""".getBytes(UTF_8)
    );
    assertSame(response, exception.httpResponse());
    final var rendered = exception.toString();
    assertTrue(rendered.startsWith("IncidentIoRequestException{"), rendered);
    assertTrue(rendered.contains("validation_error"), rendered);
  }
}
