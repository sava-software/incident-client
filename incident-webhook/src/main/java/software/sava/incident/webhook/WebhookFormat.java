package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;

import java.time.Duration;

/// Renders an [IncidentAlert] as the JSON body POSTed to a webhook endpoint, and reads the
/// wait a failed response's body asks for. This is the only seam that varies between
/// webhook receivers — transport, configuration, and error handling are shared — so
/// supporting another product is one implementation of this interface; see
/// [WebhookFormats] for the built-in formats and [TelegramTextFormat] for one that reads
/// the wait.
///
/// [#render(IncidentAlert)] is the only abstract method, so a lambda is a format, with the
/// default [#retryAfter(String)], which states no wait.
public interface WebhookFormat {

  /// Renders `alert` as a complete JSON document. Every caller-supplied string must be
  /// escaped on its way in; the result is sent verbatim as an `application/json` body.
  String render(final IncidentAlert alert);

  /// The wait a failed response's body asks for before another attempt, or null when it
  /// states none. The built-in client calls it for a non-2xx response with the response
  /// body decoded as UTF-8 (empty when there was none) and carries the result on the
  /// failure; a `Retry-After` header that parses wins over what this returns.
  ///
  /// The default states no wait. An implementation should not throw: the built-in client
  /// treats a `RuntimeException` from it as no wait.
  default Duration retryAfter(final String responseBody) {
    return null;
  }
}
