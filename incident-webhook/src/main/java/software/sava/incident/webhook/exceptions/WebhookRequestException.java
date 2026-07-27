package software.sava.incident.webhook.exceptions;

import software.sava.incident.core.api.IncidentClientException;

import java.net.http.HttpResponse;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/// A non-2xx webhook response. Receivers signal failure by status code — Slack and
/// Telegram included — so no error envelope is parsed; the raw response body is carried
/// as-is. The message deliberately omits the request URI: webhook URLs (e.g. Slack
/// incoming webhooks) carry the credential, and exception messages end up in logs.
public final class WebhookRequestException extends RuntimeException implements IncidentClientException {

  private final HttpResponse<?> httpResponse;
  private final String body;

  public WebhookRequestException(final HttpResponse<?> httpResponse, final byte[] body) {
    this(httpResponse, body == null ? "" : new String(body, UTF_8));
  }

  public WebhookRequestException(final HttpResponse<?> httpResponse, final String body) {
    super("Webhook request failed with status " + statusCode(httpResponse) + ": '" + (body == null ? "" : body) + "'");
    this.httpResponse = httpResponse;
    this.body = body == null ? "" : body;
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

  @Override
  public List<String> errors() {
    return body.isBlank() ? List.of() : List.of(body);
  }

  @Override
  public String toString() {
    return "WebhookRequestException{errorCode=" + errorCode() + ", body='" + body + "'}";
  }
}
