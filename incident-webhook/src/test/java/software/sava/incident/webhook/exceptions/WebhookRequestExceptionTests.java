package software.sava.incident.webhook.exceptions;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

  private static Duration retryAfter(final String body) {
    return new WebhookRequestException(new StubResponse(429), body).retryAfter();
  }

  /// An `ok: false` body whose `parameters` object is `parameters`.
  private static Duration retryAfterOfParameters(final String parameters) {
    return retryAfter("""
        {"ok":false,"error_code":429,"parameters":%s}""".formatted(parameters));
  }

  /// A body whose `retry_after` value is the raw JSON `value`.
  private static Duration retryAfterOfValue(final String value) {
    return retryAfterOfParameters("""
        {"retry_after":%s}""".formatted(value));
  }

  @Test
  void theBotApiBodyStatesTheWait() {
    assertEquals(Duration.ofSeconds(35), retryAfter(TOO_MANY_REQUESTS));
    // no status gate: the body alone decides
    assertEquals(Duration.ofSeconds(35),
        new WebhookRequestException(new StubResponse(400), TOO_MANY_REQUESTS).retryAfter());
    assertEquals(Duration.ofSeconds(35),
        new WebhookRequestException(new StubResponse(503), TOO_MANY_REQUESTS.getBytes(UTF_8)).retryAfter());
  }

  @Test
  void aParsedHeaderWinsOverTheBody() {
    final var header = new StubResponse(429, Map.of("Retry-After", List.of("7")));
    assertEquals(Duration.ofSeconds(7), new WebhookRequestException(header, TOO_MANY_REQUESTS).retryAfter());
    final var zero = new StubResponse(429, Map.of("Retry-After", List.of("0")));
    assertSame(Duration.ZERO, new WebhookRequestException(zero, TOO_MANY_REQUESTS).retryAfter());
    // a header that does not parse leaves the body to decide
    final var garbage = new StubResponse(429, Map.of("Retry-After", List.of("soon")));
    assertEquals(Duration.ofSeconds(35), new WebhookRequestException(garbage, TOO_MANY_REQUESTS).retryAfter());
  }

  @Test
  void aHeaderAloneStillCounts() {
    final var header = new StubResponse(429, Map.of("Retry-After", List.of("7")));
    assertEquals(Duration.ofSeconds(7), new WebhookRequestException(header, "invalid_payload").retryAfter());
  }

  @Test
  void aNullResponseLeavesTheBodyToDecide() {
    assertEquals(Duration.ofSeconds(35), new WebhookRequestException(null, TOO_MANY_REQUESTS).retryAfter());
    assertNull(new WebhookRequestException(null, (String) null).retryAfter());
  }

  @Test
  void fieldOrderDoesNotMatter() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"parameters":{"retry_after":35},"description":"Too Many Requests: retry after 35","error_code":429,"ok":false}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"parameters":{"retry_after":35},"ok":false}"""));
  }

  @Test
  void unknownFieldsAreSkipped() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"result":{"a":[1,{"b":[]},"}"],"c":null},"ok":false,"list":[[{"ok":true}],{}],\
        "parameters":{"migrate_to_chat_id":-1001234567890,"nested":{"retry_after":1},\
        "retry_after":35,"after":[true,false,"x"]},"tail":{"parameters":{"retry_after":2}}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
         { "ok" : false , "parameters" : { "retry_after" : 35 } } """));
  }

  @Test
  void okMustBeTheBooleanFalse() {
    assertNull(retryAfter("""
        {"ok":true,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":"false","parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":0,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":null,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":falsy,"parameters":{"retry_after":35}}"""));
  }

  @Test
  void parametersMustBeAnObjectHoldingRetryAfter() {
    assertNull(retryAfter("""
        {"ok":false,"error_code":429}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":null}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":[{"retry_after":35}]}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":35}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":"{\\"retry_after\\":35}"}"""));
    assertNull(retryAfterOfParameters("{}"));
    assertNull(retryAfterOfParameters("""
        {"migrate_to_chat_id":-1001234567890}"""));
  }

  @Test
  void retryAfterMustBePositiveAsciiDigits() {
    assertEquals(Duration.ofSeconds(1), retryAfterOfValue("1"));
    assertEquals(Duration.ofSeconds(35), retryAfterOfValue("35"));
    // a 9 and a 0 after the first digit, which a range check on each digit must take
    assertEquals(Duration.ofSeconds(39), retryAfterOfValue("39"));
    assertEquals(Duration.ofSeconds(1999), retryAfterOfValue("1999"));
    assertEquals(Duration.ofSeconds(100), retryAfterOfValue("100"));
    assertEquals(Duration.ofSeconds(Long.MAX_VALUE), retryAfterOfValue("9223372036854775807"));
    for (final var value : List.of(
        "0", "-5", "-0", "35.5", "35.0", "1e2", "35E1", "\"35\"", "null", "true", "false",
        "{}", "[35]", "9223372036854775808", "123456789012345678901234567890",
        // not JSON number tokens
        "035", "00", "+5", "35x", "3 5",
        // digits outside ASCII, which Long.parseLong alone would read: null end to end, as the
        // body is read as bytes and neither value is a number token there
        "3５", "٣٥")) {
      assertNull(retryAfterOfValue(value), value);
    }
  }

  @Test
  void theLastOccurrenceOfAFieldDecides() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":true,"ok":false,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"ok":true,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"ok":"false","parameters":{"retry_after":35}}"""));
    // an earlier occurrence of another type is skipped, not refused
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":"false","ok":false,"parameters":{"retry_after":35}}"""));

    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":null,"parameters":{"retry_after":35}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":[35],"parameters":{"retry_after":35}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":"35","parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":null}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":{}}"""));
    assertEquals(Duration.ofSeconds(7), retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":{"retry_after":7}}"""));

    assertEquals(Duration.ofSeconds(35), retryAfterOfParameters("""
        {"retry_after":"7","retry_after":35}"""));
    assertNull(retryAfterOfParameters("""
        {"retry_after":35,"retry_after":"7"}"""));
    assertNull(retryAfterOfParameters("""
        {"retry_after":35,"retry_after":0}"""));
    assertEquals(Duration.ofSeconds(7), retryAfterOfParameters("""
        {"retry_after":35,"retry_after":7}"""));
  }

  @Test
  void anythingElseIsNullAndNothingThrows() {
    for (final var body : List.of(
        "invalid_payload", "", "   ", "<html><body>429 Too Many Requests</body></html>",
        "null", "35", "\"text\"", "[" + TOO_MANY_REQUESTS + "]",
        TOO_MANY_REQUESTS.substring(0, TOO_MANY_REQUESTS.length() - 1),
        TOO_MANY_REQUESTS.substring(0, TOO_MANY_REQUESTS.length() - 3),
        "{\"ok\":false,\"parameters\":{\"retry_after\":35}",
        // the body must open as an object: json-iterator would read fields after a comma
        ",\"ok\":false,\"parameters\":{\"retry_after\":35}}")) {
      assertNull(retryAfter(body), body);
    }
  }

  /// `skip()` is iterative in the pinned json-iterator, so nesting depth in an ignored field
  /// cannot overflow the stack.
  @Test
  void deepNestingInAnIgnoredFieldIsSkipped() {
    final var depth = 100_000;
    final var unclosed = "{\"ok\":false,\"x\":" + "[".repeat(depth) + ",\"parameters\":{\"retry_after\":35}}";
    assertNull(retryAfter(unclosed));
    final var closed = "{\"x\":" + "[".repeat(depth) + "]".repeat(depth)
        + ",\"ok\":false,\"parameters\":{\"retry_after\":35}}";
    assertEquals(Duration.ofSeconds(35), retryAfter(closed));
  }

  @Test
  void aLargeBodyIsReadWhole() {
    final var megabyte = "a".repeat(1 << 20);
    assertNull(retryAfter(megabyte));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"description":"%s","parameters":{"retry_after":35}}""".formatted(megabyte)));
    assertNull(retryAfter("""
        {"ok":false,"description":"%s","parameters":{"retry_after":35}""".formatted(megabyte)));
  }
}
