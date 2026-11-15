package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;

import java.time.Duration;

/// Renders an [IncidentAlert] as the JSON body POSTed to a webhook endpoint, and reads the
/// wait a failed response's body asks for, and the destination it names in this one's place.
/// This is the only seam that varies between
/// webhook receivers — transport, configuration, and error handling are shared — so
/// supporting another product is one implementation of this interface; see
/// [WebhookFormats] for the built-in formats and [TelegramTextFormat] for one that reads
/// the wait and the move.
///
/// [#render(IncidentAlert)] is the only abstract method, so a lambda is a format, with the
/// defaults [#retryAfter(String)], which states no wait, and [#movedTo(String)], which states
/// no move.
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

  /// The format to send with instead, when a failed response's body says this format's
  /// destination has moved for good; null when it says no such thing, the default. The
  /// built-in incident adapter ([WebhookClient#incidentClient(WebhookFormat)]) asks only after
  /// a refusal that cannot be retried (a
  /// [software.sava.incident.webhook.exceptions.WebhookRequestException] whose `canBeRetried()`
  /// is false), with the body decoded as UTF-8 (empty when there was none). A result that is
  /// non-null and not equal to this format renders and sends the same alert once more; the
  /// answer to that request is never asked about. [WebhookClient#post(String, WebhookFormat)]
  /// never asks.
  ///
  /// An implementation should not throw: the adapter treats a `RuntimeException` from it, or
  /// from the `equals` of what it returns, as no move.
  ///
  /// When the adapter follows a move it logs both formats at WARNING, as their `toString()`
  /// gives them. A format that holds a secret must keep it out of its `toString()`; a record's
  /// generated one prints every component.
  default WebhookFormat movedTo(final String responseBody) {
    return null;
  }
}
