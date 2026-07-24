package software.sava.incident.pagerduty.event.client;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNullElse;

/// Adapts a [PagerDutyEventClient] to the provider-neutral [IncidentClient]:
/// [IncidentClient#reportIncident(IncidentAlert)] triggers an alert event on the client's
/// default route, and [IncidentClient#resolveIncident(String)] resolves by `dedup_key`.
/// The alert's `details` field is carried as a `details` entry in `custom_details`.
public final class PagerDutyIncidentClient implements IncidentClient {

  /// PagerDuty requires a payload `source`; alerts without one fall back to this.
  static final String UNKNOWN_SOURCE = "unknown";

  public static IncidentClient createClient(final PagerDutyEventClient client) {
    return new PagerDutyIncidentClient(client);
  }

  private final PagerDutyEventClient client;

  private PagerDutyIncidentClient(final PagerDutyEventClient client) {
    this.client = client;
  }

  static PagerDutySeverity mapSeverity(final IncidentSeverity severity) {
    return switch (severity) {
      case CRITICAL -> PagerDutySeverity.critical;
      case ERROR -> PagerDutySeverity.error;
      case WARNING -> PagerDutySeverity.warning;
      case INFO -> PagerDutySeverity.info;
    };
  }

  static PagerDutyEventPayload toPayload(final IncidentAlert alert) {
    final var source = alert.source();
    final var builder = PagerDutyEventPayload.build()
        .dedupKey(alert.key())
        .summary(alert.summary())
        .source(source == null || source.isBlank() ? UNKNOWN_SOURCE : source)
        .severity(mapSeverity(alert.severity()))
        .timestamp(alert.timestamp());
    final var details = alert.details();
    if (details != null && !details.isBlank()) {
      builder.customDetails("details", details);
    }
    for (final var entry : alert.customDetails().entrySet()) {
      builder.customDetails(entry.getKey(), entry.getValue());
    }
    return builder.create();
  }

  @Override
  public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
    final var payload = toPayload(alert);
    return client.triggerDefaultRouteEvent(payload).thenApply(response ->
        toResponse(response, payload.dedupKey())
    );
  }

  @Override
  public boolean supportsResolve() {
    return true;
  }

  @Override
  public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
    return client.resolveEvent(key).thenApply(response -> toResponse(response, key));
  }

  private static IncidentResponse toResponse(final PagerDutyEventResponse response, final String dedupKey) {
    return IncidentResponse.of(
        requireNonNullElse(response.dedupKey(), dedupKey),
        response.status(),
        null
    );
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
    return "PagerDutyIncidentClient{client=" + client + '}';
  }
}
