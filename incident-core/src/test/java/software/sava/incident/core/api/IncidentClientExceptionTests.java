package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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

    /// `Retry-After` and `Date`, each omitted when null.
    static HeaderResponse retryAfterAndDate(final String retryAfter, final String date) {
      final var headers = new HashMap<String, List<String>>();
      if (retryAfter != null) {
        headers.put("Retry-After", List.of(retryAfter));
      }
      if (date != null) {
        headers.put("Date", List.of(date));
      }
      return new HeaderResponse(HttpHeaders.of(headers, (_, _) -> true));
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

  /// RFC 9110's delay-seconds: ASCII digits whose value fits a long, in seconds.
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

  /// Anything but delta-seconds is no wait on a response without a `Date` header, an
  /// HTTP-date included: there is nothing to measure it from.
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

  /// A value that is neither delta-seconds nor an HTTP-date is no wait, whatever the `Date`.
  @Test
  void everyOtherFormIsNullBesideAValidDate() {
    for (final var value : List.of(
        "+30", "-30", "-0", "30.5", "1e3", "3 0", "30s", "0x1E",
        "   ",
        "٣٠",
        "３０",
        "2026-11-12T10:00:30Z",
        // a weekday that is not the date's
        "Wed, 12 Nov 2026 10:00:30 GMT",
        "yesterday")) {
      assertNull(IncidentClientException.retryAfter(HeaderResponse.retryAfterAndDate(value, DATE)), value);
    }
  }

  private static final String DATE = "Thu, 12 Nov 2026 10:00:00 GMT";

  private static Duration parse(final String retryAfter, final String date) {
    return IncidentClientException.retryAfter(HeaderResponse.retryAfterAndDate(retryAfter, date));
  }

  /// RFC 9110's HTTP-date: the wait is the time from the response's own `Date` to it.
  @Test
  void anHttpDateIsMeasuredFromTheResponsesDate() {
    assertEquals(Duration.ofSeconds(30), parse("Thu, 12 Nov 2026 10:00:30 GMT", DATE));
    assertEquals(Duration.ofSeconds(1), parse("Thu, 12 Nov 2026 10:00:01 GMT", DATE));
    assertEquals(Duration.ofHours(5).plusMinutes(30), parse("Thu, 12 Nov 2026 15:30:00 GMT", DATE));
    assertEquals(Duration.ofHours(16).plusMinutes(15).plusSeconds(45), parse("Fri, 13 Nov 2026 02:15:45 GMT", DATE));
    assertEquals(Duration.ofSeconds(90), parse("Fri, 13 Nov 2026 00:00:30 GMT", "Thu, 12 Nov 2026 23:59:00 GMT"));
    // a real leap day, on either header
    assertEquals(Duration.ofDays(1), parse("Fri, 01 Mar 2024 00:00:00 GMT", "Thu, 29 Feb 2024 00:00:00 GMT"));
    assertEquals(Duration.ofDays(1), parse("Thu, 29 Feb 2024 00:00:00 GMT", "Wed, 28 Feb 2024 00:00:00 GMT"));
  }

  /// Only an IMF-fixdate exactly as RFC 9110 section 5.6.7 writes it is read, on either header:
  /// case-sensitive English names, fixed widths, the literal `GMT`, and a date and time that
  /// exist. Each value here stands for `Thu, 12 Nov 2026 10:00:30 GMT` or is not a real date.
  private static final List<String> NOT_IMF_FIXDATES = List.of(
      // a two-digit and a five-digit year
      "Thu, 12 Nov 26 10:00:30 GMT",
      "Thu, 12 Nov 02026 10:00:30 GMT",
      // days that do not exist, each with the weekday of the day a lenient reader clamps to
      "Sat, 30 Feb 2026 10:00:30 GMT",
      "Sat, 29 Feb 2026 10:00:30 GMT",
      "Mon, 31 Nov 2026 10:00:30 GMT",
      // times that do not exist, the leap second included
      "Thu, 12 Nov 2026 24:00:00 GMT",
      "Thu, 12 Nov 2026 10:60:30 GMT",
      "Thu, 12 Nov 2026 10:00:60 GMT",
      "Thu, 12 Nov 2026 23:59:60 GMT",
      // a weekday that contradicts the date
      "Fri, 12 Nov 2026 10:00:30 GMT",
      // a one-digit day, missing seconds, a missing weekday
      "Mon, 2 Nov 2026 10:00:30 GMT",
      "Thu, 12 Nov 2026 10:00 GMT",
      "12 Nov 2026 10:00:30 GMT",
      // lower-case names
      "thu, 12 nov 2026 10:00:30 gmt",
      "Thu, 12 nov 2026 10:00:30 GMT",
      "Thu, 12 Nov 2026 10:00:30 gmt",
      // a zone other than the literal GMT
      "Thu, 12 Nov 2026 10:00:30 UTC",
      "Thu, 12 Nov 2026 10:00:30 Z",
      "Thu, 12 Nov 2026 10:00:30 +0000",
      "Thu, 12 Nov 2026 11:00:30 +0100",
      // a doubled space
      "Thu,  12 Nov 2026 10:00:30 GMT",
      "Thu, 12 Nov 2026  10:00:30 GMT",
      "Thu, 12 Nov 2026 10:00:30  GMT");

  /// A `Retry-After` that is not a strict IMF-fixdate is null beside a valid `Date`.
  @Test
  void aRetryAfterThatIsNotAStrictImfFixdateIsNull() {
    for (final var value : NOT_IMF_FIXDATES) {
      assertNull(parse(value, DATE), value);
    }
  }

  /// A `Date` that is not a strict IMF-fixdate is null, and so is a valid date-form
  /// `Retry-After` measured from it.
  @Test
  void aDateThatIsNotAStrictImfFixdateIsNull() {
    for (final var value : NOT_IMF_FIXDATES) {
      assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, value)), value);
      assertNull(parse("Thu, 12 Nov 2026 10:05:00 GMT", value), value);
    }
  }

  /// An HTTP-date's year is 1900 or later (RFC 5322 section 3.3 through RFC 9110 section
  /// 5.6.7). A `Retry-After` in 1899 is null rather than a date already past, which would be
  /// zero; a `Date` in 1899 is no `Date`, so a valid date-form `Retry-After` has nothing to be
  /// measured from.
  @Test
  void anHttpDateBefore1900IsNullOnEitherHeader() {
    final var lastSecondOf1899 = "Sun, 31 Dec 1899 23:59:59 GMT";
    assertNull(parse(lastSecondOf1899, DATE));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, lastSecondOf1899)));
    assertNull(parse("Thu, 12 Nov 2026 10:05:00 GMT", lastSecondOf1899));
    // the first second of 1900 is read on either header
    final var firstSecondOf1900 = "Mon, 01 Jan 1900 00:00:00 GMT";
    assertEquals(Duration.ZERO, parse(firstSecondOf1900, DATE));
    assertEquals(Instant.parse("1900-01-01T00:00:00Z"),
        IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, firstSecondOf1900)));
    assertEquals(Duration.ofSeconds(30), parse("Mon, 01 Jan 1900 00:00:30 GMT", firstSecondOf1900));
  }

  /// A date at or before the response's `Date` is no wait left: zero.
  @Test
  void anHttpDateNotAfterTheResponsesDateIsZero() {
    assertEquals(Duration.ZERO, parse(DATE, DATE));
    assertEquals(Duration.ZERO, parse("Thu, 12 Nov 2026 09:59:59 GMT", DATE));
    assertEquals(Duration.ZERO, parse("Wed, 21 Oct 2015 07:28:00 GMT", DATE));
  }

  /// Without a `Date` that parses, an HTTP-date has nothing to be measured from: null.
  @Test
  void anHttpDateWithoutAReadableDateIsNull() {
    final var retryAfter = "Thu, 12 Nov 2026 10:00:30 GMT";
    assertNull(parse(retryAfter, null), "no Date");
    for (final var date : List.of(
        "2026-11-12T10:00:00Z",
        "Thursday, 12-Nov-26 10:00:00 GMT",
        "Thu Nov 12 10:00:00 2026",
        "",
        "yesterday")) {
      assertNull(parse(retryAfter, date), date);
    }
  }

  /// The obsolete rfc850-date and asctime-date forms are not read, whatever the `Date`.
  @Test
  void theObsoleteDateFormsAreNull() {
    assertNull(parse("Sunday, 06-Nov-94 08:49:37 GMT", "Sun, 06 Nov 1994 08:49:00 GMT"));
    assertNull(parse("Sun Nov  6 08:49:37 1994", "Sun, 06 Nov 1994 08:49:00 GMT"));
    assertNull(parse("Thursday, 12-Nov-26 10:00:30 GMT", DATE));
    assertNull(parse("Thu Nov 12 10:00:30 2026", DATE));
  }

  /// A numeric zone offset in place of `GMT`, on either header, is null.
  @Test
  void aNumericZoneOffsetIsNull() {
    assertNull(parse("Thu, 12 Nov 2026 11:00:30 +0100", DATE));
    assertNull(parse("Thu, 12 Nov 2026 10:00:30 GMT", "Thu, 12 Nov 2026 11:00:00 +0100"));
    assertNull(parse("Thu, 12 Nov 2026 10:30:00 -0200", "Thu, 12 Nov 2026 12:00:00 +0100"));
    assertNull(parse("Thu, 12 Nov 2026 10:00:30 +0100", DATE));
  }

  /// delay-seconds is read as it is, a `Date` header beside it or not, and the first value
  /// decides between the two forms.
  @Test
  void delaySecondsIgnoresTheDate() {
    assertEquals(Duration.ofSeconds(30), parse("30", DATE));
    assertEquals(Duration.ZERO, parse("0", DATE));
    assertEquals(Duration.ofSeconds(30), parse("30", "yesterday"));
    assertNull(parse("9223372036854775808", DATE));
    assertNull(parse("", DATE));
    final var dateFirst = Map.of(
        "Retry-After", List.of("Thu, 12 Nov 2026 10:00:30 GMT", "5"),
        "Date", List.of(DATE));
    assertEquals(Duration.ofSeconds(30), IncidentClientException.retryAfter(
        new HeaderResponse(HttpHeaders.of(dateFirst, (_, _) -> true))));
    final var digitsFirst = Map.of(
        "Retry-After", List.of("5", "Thu, 12 Nov 2026 10:00:30 GMT"),
        "Date", List.of(DATE));
    assertEquals(Duration.ofSeconds(5), IncidentClientException.retryAfter(
        new HeaderResponse(HttpHeaders.of(digitsFirst, (_, _) -> true))));
  }

  /// The `Date` header as an instant, or null when there is none that parses.
  @Test
  void responseDate() {
    assertEquals(Instant.parse("2026-11-12T10:00:00Z"),
        IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, DATE)));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, "Thu, 12 Nov 2026 11:00:00 +0100")));
    assertEquals(Instant.parse("2026-11-12T10:00:00Z"), IncidentClientException.responseDate(new HeaderResponse(
        HttpHeaders.of(Map.of("date", List.of(DATE, "yesterday")), (_, _) -> true))));
    assertNull(IncidentClientException.responseDate(null));
    assertNull(IncidentClientException.responseDate(new HeaderResponse(null)));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate("30", null)));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, "yesterday")));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, "")));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, "2026-11-12T10:00:00Z")));
    assertNull(IncidentClientException.responseDate(HeaderResponse.retryAfterAndDate(null, "Sun Nov  6 08:49:37 1994")));
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
    assertEquals(Duration.ofSeconds(30), new DefaultException(
        HeaderResponse.retryAfterAndDate("Thu, 12 Nov 2026 10:00:30 GMT", DATE)).retryAfter());
    assertNull(new DefaultException(null).retryAfter());
  }
}
