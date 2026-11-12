package software.sava.incident.webhook.exceptions;

import software.sava.incident.core.api.IncidentClientException;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/// A non-2xx webhook response. Receivers signal failure by status code — Slack and
/// Telegram included — so no error envelope is parsed; the raw response body is carried
/// as-is. The wait a body states is read by the request's
/// [software.sava.incident.webhook.WebhookFormat]
/// ([software.sava.incident.webhook.WebhookFormat#retryAfter(String)]) and handed in. The
/// message deliberately omits the request URI: webhook URLs (e.g. Slack incoming webhooks)
/// carry the credential, and exception messages end up in logs.
public final class WebhookRequestException extends RuntimeException implements IncidentClientException {

  private final HttpResponse<?> httpResponse;
  private final String body;
  private final Duration retryAfter;

  /// A failure carrying no wait from its body, which is not read for one: only a
  /// `Retry-After` header that parses states a wait.
  public WebhookRequestException(final HttpResponse<?> httpResponse, final byte[] body) {
    this(httpResponse, body == null ? "" : new String(body, UTF_8));
  }

  /// A failure carrying no wait from its body, which is not read for one: only a
  /// `Retry-After` header that parses states a wait.
  public WebhookRequestException(final HttpResponse<?> httpResponse, final String body) {
    this(httpResponse, body, null);
  }

  /// `retryAfter` is the wait the response body states, as the request's format read it;
  /// null for none.
  public WebhookRequestException(final HttpResponse<?> httpResponse,
                                 final String body,
                                 final Duration retryAfter) {
    super("Webhook request failed with status " + statusCode(httpResponse) + ": '" + (body == null ? "" : body) + "'");
    this.httpResponse = httpResponse;
    this.body = body == null ? "" : body;
    this.retryAfter = retryAfter;
  }

  private static int statusCode(final HttpResponse<?> httpResponse) {
    return httpResponse == null ? -1 : httpResponse.statusCode();
  }

  /// The raw response body, decoded as UTF-8; empty when the response had none.
  public String body() {
    return body;
  }

  @Override
  public boolean canBeRetried() {
    return httpResponse == null
        || httpResponse.statusCode() >= 500
        || httpResponse.statusCode() == 429;
  }

  @Override
  public HttpResponse<?> httpResponse() {
    return httpResponse;
  }

  /// The HTTP status code, or -1 when no response was observed.
  @Override
  public long errorCode() {
    return statusCode(httpResponse);
  }

  /// The wait the receiver asked for: the `Retry-After` header when it parses, in either of
  /// its forms ([IncidentClientException#retryAfter(HttpResponse)]), `0` included;
  /// otherwise the wait the body states as the request's format read it, given to the
  /// constructor; otherwise null. Nothing is parsed from the body here. Never throws.
  @Override
  public Duration retryAfter() {
    final var header = IncidentClientException.retryAfter(httpResponse);
    return header != null ? header : retryAfter;
  }

  @Override
  public List<String> errors() {
    return body.isBlank() ? List.of() : List.of(body);
  }

  @Override
  public String toString() {
    return "WebhookRequestException{errorCode=" + errorCode() + ", body='" + body + "'}";
  }
}
