package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.exceptions.PagerDutyParseException;
import software.sava.incident.pagerduty.exceptions.PagerDutyRequestException;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Direct tests for the error-envelope mapping in [PagerDutyEventClientImpl] — the
/// status mapping and envelope parsing are in-process logic reachable through the
/// package-private `errorResponse`; only the transport methods need a wire.
final class PagerDutyEventClientErrorTests {

  private record StubResponse(int statusCode, byte[] body) implements HttpResponse<byte[]> {

    @Override
    public HttpRequest request() {
      return HttpRequest.newBuilder(URI.create("https://events.pagerduty.com/v2/enqueue")).build();
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
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return URI.create("https://events.pagerduty.com/v2/enqueue");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_2;
    }
  }

  private static StubResponse response(final int statusCode, final String body) {
    return new StubResponse(statusCode, body.getBytes(UTF_8));
  }

  private static PagerDutyRequestException requestException(final HttpResponse<?> response) {
    return assertInstanceOf(PagerDutyRequestException.class,
        assertThrows(RuntimeException.class, () -> PagerDutyEventClientImpl.errorResponse(response)));
  }

  @Test
  void tooManyRequestsThrowsBeforeReadingTheBody() {
    final var thrown = requestException(new StubResponse(429, null));
    assertEquals("Too many requests", thrown.getMessage());
    assertTrue(thrown.canBeRetried());
  }

  @Test
  void notFoundNamesTheRequestUri() {
    final var thrown = requestException(new StubResponse(404, null));
    assertEquals("https://events.pagerduty.com/v2/enqueue Not Found", thrown.getMessage());
    assertFalse(thrown.canBeRetried());
  }

  @Test
  void envelopeFieldsOverrideTheDefaultMessage() {
    final var thrown = requestException(response(400, """
        {"status":"invalid event","message":"Event object is invalid","errors":["'summary' is missing"],"unknown":1}"""));
    assertEquals("Event object is invalid", thrown.getMessage());
    assertEquals("invalid event", thrown.status());
    assertEquals(List.of("'summary' is missing"), thrown.errors());
    assertFalse(thrown.canBeRetried());
  }

  @Test
  void emptyEnvelopeKeepsTheStatusCodeDefaults() {
    assertEquals("Bad Request - Check that the JSON is valid.",
        requestException(response(400, "{}")).getMessage());
    assertEquals("Unauthorized", requestException(response(401, "{}")).getMessage());
    assertEquals("Payment Required", requestException(response(402, "{}")).getMessage());
    assertEquals("Forbidden", requestException(response(403, "{}")).getMessage());
    assertEquals("Internal Server Error - the PagerDuty server experienced an error while processing the event.",
        requestException(response(500, "{}")).getMessage());
    // no default for other codes: the message is whatever the envelope carries;
    // 600 sits just above the 5xx band
    assertNull(requestException(response(418, "{}")).getMessage());
    assertNull(requestException(response(600, "{}")).getMessage());
  }

  @Test
  void retriableStatusCodes() {
    assertTrue(requestException(response(500, "{}")).canBeRetried());
    assertTrue(requestException(response(503, "{}")).canBeRetried());
    assertTrue(requestException(new StubResponse(429, null)).canBeRetried());
    // 499 sits just below the 5xx boundary
    assertFalse(requestException(response(499, "{}")).canBeRetried());
    assertFalse(requestException(response(400, "{}")).canBeRetried());
  }

  @Test
  void multipleErrorsAreCollected() {
    final var thrown = requestException(response(400, """
        {"status":"invalid event","errors":["one","two","three"]}"""));
    assertEquals(List.of("one", "two", "three"), thrown.errors());
    // no envelope message: the 400 default stands
    assertEquals("Bad Request - Check that the JSON is valid.", thrown.getMessage());
  }

  @Test
  void malformedEnvelopeThrowsParseException() {
    final var thrown = assertInstanceOf(PagerDutyParseException.class,
        assertThrows(RuntimeException.class, () ->
            PagerDutyEventClientImpl.errorResponse(response(502, "<html>bad gateway</html>"))));
    assertTrue(thrown.getMessage().contains("502"), thrown.getMessage());
    assertTrue(thrown.getMessage().contains("<html>bad gateway</html>"), thrown.getMessage());
    assertNotNull(thrown.getCause());
    assertTrue(thrown.canBeRetried());
  }

  @Test
  void clientConstructionExposesDefaultsAndDescribesItself() {
    final var client = PagerDutyEventClient.clientBuilder()
        .defaultClientName("client-1")
        .defaultClientUrl("https://example.com")
        .defaultRoutingKey("rk-1")
        .createClient();
    assertEquals("client-1", client.defaultClientName());
    assertEquals("https://example.com", client.defaultClientUrl());
    assertEquals("rk-1", client.defaultRoutingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), client.endpoint());
    assertNotNull(client.httpClient());
    assertTrue(client.toString().startsWith("PagerDutyEventClientImpl{"), client.toString());
  }
}
