package software.sava.incident.core.api;

import java.net.http.HttpResponse;
import java.time.Duration;
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
  /// The default reads the `Retry-After` header of [#httpResponse()] through
  /// [#retryAfter(HttpResponse)]; a provider that also states the wait elsewhere, such as in
  /// its error body, overrides this.
  default Duration retryAfter() {
    return retryAfter(httpResponse());
  }

  /// The `Retry-After` header of `httpResponse` in its delta-seconds form (RFC 9110 section
  /// 10.2.3): ASCII digits only, surrounding whitespace aside, whose value fits a `long`.
  /// `0` is [Duration#ZERO].
  ///
  /// Null for anything else: a sign, a fraction, an empty value, a value over
  /// [Long#MAX_VALUE], a missing header, a null response or null headers. The HTTP-date form
  /// is deliberately not supported, and is null too. Never throws.
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
        return null;
      }
    }
    try {
      return Duration.ofSeconds(Long.parseLong(value));
    } catch (final NumberFormatException emptyOrOverLongMaxValue) {
      return null;
    }
  }
}
