package software.sava.incident.core.api;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/// Implemented by each provider's request/parse exceptions so retry logic can be written
/// against the common client interface.
public interface IncidentClientException {

  boolean canBeRetried();

  HttpResponse<?> httpResponse();

  long errorCode();

  List<String> errors();

  /// The wait the provider asked for before another attempt, or null when it asked for none.
  /// [IncidentService] waits it out before its next attempt, up to the service's limit.
  ///
  /// The default reads the `Retry-After` header of [#httpResponse()], in seconds or as a strict
  /// IMF-fixdate measured from the response's `Date`, through [#retryAfter(HttpResponse)],
  /// which states the rule and its limits; a provider that also states the wait elsewhere,
  /// such as in its error body, overrides this.
  default Duration retryAfter() {
    return retryAfter(httpResponse());
  }

  /// The `Retry-After` header of `httpResponse` (RFC 9110 section 10.2.3), its first value, in
  /// either of its two forms:
  ///
  /// - delay-seconds: ASCII digits only, surrounding whitespace aside, whose value fits a
  ///   `long`. `0` is [Duration#ZERO]; a sign, a fraction, an empty value or a value over
  ///   [Long#MAX_VALUE] is null.
  /// - HTTP-date: any other value must be exactly an IMF-fixdate as RFC 9110 section 5.6.7
  ///   writes it, such as `Thu, 12 Nov 2026 10:05:00 GMT`:
  ///   `day-name "," SP 2DIGIT SP month SP 4DIGIT SP 2DIGIT ":" 2DIGIT ":" 2DIGIT SP "GMT"`,
  ///   with case-sensitive English three-letter names (`Mon`..`Sun`, `Jan`..`Dec`), fixed
  ///   widths, the literal `GMT`, and a real date and time: the weekday matches the date, the
  ///   day exists in its month and year, the hour is 00-23, the minute and second 00-59, and
  ///   the year is 1900 or later (RFC 9110 section 5.6.7 gives it the meaning RFC 5322
  ///   section 3.3 gives it). Anything else is null, such as lower-case names, a missing
  ///   weekday or seconds, a one-digit day, a year that is not four digits, a year before
  ///   1900, a day that does not exist, `24:00:00`, or a zone other than `GMT`. The wait is
  ///   the time from the response's own `Date` header ([#responseDate(HttpResponse)], read by
  ///   the same rule) to that date, and [Duration#ZERO] when the date is not after it.
  ///   Measuring from `Date` rather than the local clock makes the wait count from the
  ///   response, as delay-seconds does, so the local clock's skew against the server's never
  ///   matters.
  ///
  /// The limits of the date form, stated here once: a response without a readable `Date` has
  /// nothing to measure from, and its date form is null (RFC 9110 lets a 5xx omit `Date`);
  /// the obsolete rfc850-date and asctime-date forms are null although RFC 9110 section 5.6.7
  /// asks a recipient to accept them; and a leap second (`:60`) is null.
  ///
  /// Null too for a missing header, a null response or null headers. The result depends on
  /// the response alone: no clock is read. Never throws.
  static Duration retryAfter(final HttpResponse<?> httpResponse) {
    if (httpResponse == null) {
      return null;
    }
    final var headers = httpResponse.headers();
    if (headers == null) {
      return null;
    }
    // HttpHeaders trims every value, so surrounding whitespace never reaches this point
    final var value = headers.firstValue("Retry-After").orElse("");
    // Long.parseLong would also take a sign and non-ASCII digits
    for (int i = 0; i < value.length(); ++i) {
      final char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return untilHttpDate(value, httpResponse);
      }
    }
    try {
      return Duration.ofSeconds(Long.parseLong(value));
    } catch (final NumberFormatException emptyOrOverLongMaxValue) {
      return null;
    }
  }

  /// The time from the response's `Date` to the HTTP-date `value`, [Duration#ZERO] when it is
  /// not ahead, or null when either does not parse.
  private static Duration untilHttpDate(final String value, final HttpResponse<?> httpResponse) {
    final var until = ImfFixdate.parse(value);
    if (until == null) {
      return null;
    }
    final var date = responseDate(httpResponse);
    if (date == null) {
      return null;
    }
    final var wait = Duration.between(date, until);
    return wait.isPositive() ? wait : Duration.ZERO;
  }

  /// The `Date` header of `httpResponse` (RFC 9110 section 6.6.1), its first value, as an
  /// instant: exactly an IMF-fixdate such as `Thu, 12 Nov 2026 10:00:00 GMT`, by the rule and
  /// with the limits [#retryAfter(HttpResponse)] states for its date form.
  ///
  /// Null for a null response, null headers, a missing header, or any value that is not
  /// such an IMF-fixdate. Never throws.
  static Instant responseDate(final HttpResponse<?> httpResponse) {
    if (httpResponse == null) {
      return null;
    }
    final var headers = httpResponse.headers();
    if (headers == null) {
      return null;
    }
    return ImfFixdate.parse(headers.firstValue("Date").orElse(null));
  }
}
