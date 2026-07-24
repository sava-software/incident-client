package software.sava.incident.io.exceptions;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
