package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/// Adapts a [WebhookClient] to the provider-neutral [IncidentClient]: reporting an
/// incident POSTs the alert rendered by the configured [WebhookFormat].
///
/// A webhook message is fire-and-forget notification, not incident management — there is
/// no incident id, permalink, or lifecycle. [IncidentResponse#key()] echoes the alert's
/// key, [IncidentResponse#status()] is the receiver's stripped response body (`ok` for
/// Slack) or `delivered` when the body is empty, and [IncidentResponse#url()] is null.
/// [IncidentClient#supportsResolve()] is false and [IncidentClient#resolveIncident(String)]
/// fails the returned future with an UnsupportedOperationException.
public final class WebhookIncidentClient implements IncidentClient {

  /// Fallback [IncidentResponse#status()] for an empty 2xx response body.
  static final String DELIVERED = "delivered";

  public static IncidentClient createClient(final WebhookClient client, final WebhookFormat format) {
    return new WebhookIncidentClient(
        Objects.requireNonNull(client, "'client' is required."),
        Objects.requireNonNull(format, "'format' is required.")
    );
  }

  private final WebhookClient client;
  private final WebhookFormat format;

  private WebhookIncidentClient(final WebhookClient client, final WebhookFormat format) {
    this.client = client;
    this.format = format;
  }

  WebhookFormat format() {
    return format;
  }

  @Override
  public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
    return client.post(format.render(alert)).thenApply(responseBody ->
        IncidentResponse.of(
            alert.key(),
            responseBody.isBlank() ? DELIVERED : responseBody.strip(),
            null
        )
    );
  }

  @Override
  public boolean supportsResolve() {
    return false;
  }

  @Override
  public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException(
        "A webhook message has no incident lifecycle; resolve is not supported."
    ));
  }

  @Override
  public URI endpoint() {
    return client.endpoint();
  }

  @Override
  public HttpClient httpClient() {
    return client.httpClient();
  }

  @Override
  public String toString() {
    return "WebhookIncidentClient{format=" + format + ", client=" + client + '}';
  }
}
