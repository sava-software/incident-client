package software.sava.incident.core.api;

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

import static org.junit.jupiter.api.Assertions.*;

final class IncidentClientExceptionTests {

  /// A response whose headers are the given ones, or null when `headers` is.
  private record HeaderResponse(HttpHeaders headers) implements HttpResponse<Object> {

    static HeaderResponse retryAfter(final String... values) {
      return new HeaderResponse(HttpHeaders.of(Map.of("Retry-After", List.of(values)), (_, _) -> true));
    }

    @Override
    public int statusCode() {
      return 429;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public Object body() {
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
      return HttpClient.Version.HTTP_1_1;
    }
  }

  /// An exception that keeps the interface's default `retryAfter()`.
  private record DefaultException(HttpResponse<?> httpResponse) implements IncidentClientException {

    @Override
    public boolean canBeRetried() {
      return true;
    }

    @Override
    public long errorCode() {
      return 0;
    }

    @Override
    public List<String> errors() {
      return List.of();
    }
  }

  private static Duration parse(final String value) {
    return IncidentClientException.retryAfter(HeaderResponse.retryAfter(value));
  }

  /// RFC 9110's delta-seconds: ASCII digits whose value fits a long, in seconds.
  @Test
  void deltaSecondsAreAccepted() {
    assertEquals(Duration.ofSeconds(30), parse("30"));
    assertEquals(Duration.ofSeconds(7), parse("007"));
    assertEquals(Duration.ofSeconds(1), parse("1"));
    assertEquals(Duration.ZERO, parse("0"));
    assertEquals(Duration.ofSeconds(Long.MAX_VALUE), parse("9223372036854775807"));
    // HttpHeaders trims surrounding whitespace before the header is read
    assertEquals(Duration.ofSeconds(30), parse(" \t30\t "));
  }

  /// Anything but delta-seconds is no wait: the HTTP-date form is deliberately unsupported.
  @Test
  void everyOtherFormIsNull() {
    for (final var value : List.of(
        "Wed, 21 Oct 2015 07:28:00 GMT",
        "+30", "-30", "-0", "30.5", "1e3", "3 0", "30s", "0x1E",
        "",
        "   ",
        "9223372036854775808",
        "99999999999999999999999",
        // non-ASCII digits Long.parseLong would read
        "٣٠",
        "３０")) {
      assertNull(parse(value), value);
    }
  }

  /// The first value is the one read.
  @Test
  void theFirstValueIsRead() {
    assertEquals(Duration.ofSeconds(5), IncidentClientException.retryAfter(HeaderResponse.retryAfter("5", "9")));
  }

  /// No response, no headers, or no header: no wait, and nothing thrown.
  @Test
  void aMissingResponseHeadersOrHeaderIsNull() {
    assertNull(IncidentClientException.retryAfter(null));
    assertNull(IncidentClientException.retryAfter(new HeaderResponse(null)));
    assertNull(IncidentClientException.retryAfter(new HeaderResponse(HttpHeaders.of(Map.of(), (_, _) -> true))));
    assertNull(IncidentClientException.retryAfter(new HeaderResponse(
        HttpHeaders.of(Map.of("Retry-After-Other", List.of("30")), (_, _) -> true))));
  }

  /// The header name is matched without regard to case, as HTTP names are.
  @Test
  void theHeaderNameIsCaseInsensitive() {
    assertEquals(Duration.ofSeconds(12), IncidentClientException.retryAfter(new HeaderResponse(
        HttpHeaders.of(Map.of("retry-after", List.of("12")), (_, _) -> true))));
  }

  /// The default reads the exception's own response.
  @Test
  void theDefaultReadsTheExceptionsResponse() {
    assertEquals(Duration.ofSeconds(42), new DefaultException(HeaderResponse.retryAfter("42")).retryAfter());
    assertNull(new DefaultException(null).retryAfter());
  }
}
