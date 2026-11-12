package software.sava.incident.webhook.exceptions;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

final class WebhookRequestExceptionTests {

  private record StubResponse(int statusCode, Map<String, List<String>> headerMap) implements HttpResponse<byte[]> {

    private StubResponse(final int statusCode) {
      this(statusCode, Map.of());
    }

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
      return HttpHeaders.of(headerMap, (_, _) -> true);
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
      return URI.create("https://hooks.example.com/T000/B000/secret");
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_2;
    }
  }

  @Test
  void carriesStatusAndBody() {
    final var response = new StubResponse(400);
    final var exception = new WebhookRequestException(response, "invalid_payload".getBytes(UTF_8));
    assertEquals(400L, exception.errorCode());
    assertSame(response, exception.httpResponse());
    assertEquals("invalid_payload", exception.body());
    assertEquals(List.of("invalid_payload"), exception.errors());
    assertEquals("Webhook request failed with status 400: 'invalid_payload'", exception.getMessage());
    assertEquals("WebhookRequestException{errorCode=400, body='invalid_payload'}", exception.toString());
    assertFalse(exception.canBeRetried());
  }

  @Test
  void nullBodyBytesReadAsEmpty() {
    final var exception = new WebhookRequestException(new StubResponse(502), (byte[]) null);
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
    assertEquals("Webhook request failed with status 502: ''", exception.getMessage());
  }

  @Test
  void messageOmitsTheRequestUri() {
    final var exception = new WebhookRequestException(new StubResponse(404), "not found");
    // the webhook URL is the credential; it must never leak through the message
    assertFalse(exception.getMessage().contains("hooks.example.com"));
    assertFalse(exception.toString().contains("hooks.example.com"));
  }

  @Test
  void retryBoundaries() {
    assertFalse(new WebhookRequestException(new StubResponse(400), "").canBeRetried());
    assertFalse(new WebhookRequestException(new StubResponse(404), "").canBeRetried());
    assertFalse(new WebhookRequestException(new StubResponse(499), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(500), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(503), "").canBeRetried());
    assertTrue(new WebhookRequestException(new StubResponse(429), "").canBeRetried());
  }

  @Test
  void nullResponseCanBeRetried() {
    final var exception = new WebhookRequestException(null, (String) null);
    assertTrue(exception.canBeRetried());
    assertEquals(-1L, exception.errorCode());
    assertNull(exception.httpResponse());
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
    assertEquals("Webhook request failed with status -1: ''", exception.getMessage());
  }

  @Test
  void emptyBodyYieldsNoErrors() {
    final var exception = new WebhookRequestException(new StubResponse(500), new byte[0]);
    assertEquals("", exception.body());
    assertEquals(List.of(), exception.errors());
  }

  /// The Bot API's 429 body exactly as its server writes it.
  private static final String TOO_MANY_REQUESTS = """
      {"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}""";

  private static final Duration SUPPLIED = Duration.ofSeconds(35);

  @Test
  void theSuppliedWaitIsReturnedWithoutAHeader() {
    assertSame(SUPPLIED, new WebhookRequestException(new StubResponse(429), "busy", SUPPLIED).retryAfter());
    assertSame(SUPPLIED, new WebhookRequestException(null, "busy", SUPPLIED).retryAfter());
    assertNull(new WebhookRequestException(new StubResponse(429), "busy", null).retryAfter());
    assertNull(new WebhookRequestException(null, null, null).retryAfter());
  }

  @Test
  void aParsedHeaderWinsOverTheSuppliedWait() {
    final var header = new StubResponse(429, Map.of("Retry-After", List.of("7")));
    assertEquals(Duration.ofSeconds(7), new WebhookRequestException(header, TOO_MANY_REQUESTS, SUPPLIED).retryAfter());
    final var zero = new StubResponse(429, Map.of("Retry-After", List.of("0")));
    assertSame(Duration.ZERO, new WebhookRequestException(zero, TOO_MANY_REQUESTS, SUPPLIED).retryAfter());
    // a header that does not parse leaves the supplied wait to decide
    final var garbage = new StubResponse(429, Map.of("Retry-After", List.of("soon")));
    assertSame(SUPPLIED, new WebhookRequestException(garbage, TOO_MANY_REQUESTS, SUPPLIED).retryAfter());
  }

  @Test
  void aHeaderAloneStillCounts() {
    final var header = new StubResponse(429, Map.of("Retry-After", List.of("7")));
    assertEquals(Duration.ofSeconds(7), new WebhookRequestException(header, "invalid_payload").retryAfter());
  }

  /// The two-argument constructors read no body: even the Bot API's 429 states no wait.
  @Test
  void theTwoArgumentConstructorsStateNoBodyWait() {
    assertNull(new WebhookRequestException(new StubResponse(429), TOO_MANY_REQUESTS).retryAfter());
    assertNull(new WebhookRequestException(new StubResponse(429), TOO_MANY_REQUESTS.getBytes(UTF_8)).retryAfter());
    assertNull(new WebhookRequestException(null, TOO_MANY_REQUESTS).retryAfter());
  }

  /// The wait changes nothing else the failure reports.
  @Test
  void theSuppliedWaitLeavesTheRestUnchanged() {
    for (final var response : Arrays.asList(new StubResponse(429), null)) {
      for (final var body : Arrays.asList(TOO_MANY_REQUESTS, "", null)) {
        final var withWait = new WebhookRequestException(response, body, SUPPLIED);
        final var without = new WebhookRequestException(response, body);
        assertEquals(without.getMessage(), withWait.getMessage());
        assertEquals(without.toString(), withWait.toString());
        assertEquals(without.body(), withWait.body());
        assertEquals(without.errors(), withWait.errors());
        assertEquals(without.errorCode(), withWait.errorCode());
        assertEquals(without.canBeRetried(), withWait.canBeRetried());
        assertSame(response, withWait.httpResponse());
      }
    }
    final var exception = new WebhookRequestException(new StubResponse(429), TOO_MANY_REQUESTS, SUPPLIED);
    assertEquals("Webhook request failed with status 429: '" + TOO_MANY_REQUESTS + "'", exception.getMessage());
    assertEquals("WebhookRequestException{errorCode=429, body='" + TOO_MANY_REQUESTS + "'}", exception.toString());
    assertEquals(TOO_MANY_REQUESTS, exception.body());
    assertEquals(List.of(TOO_MANY_REQUESTS), exception.errors());
  }
}
