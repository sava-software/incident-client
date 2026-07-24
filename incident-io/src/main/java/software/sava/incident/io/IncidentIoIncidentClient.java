package software.sava.incident.io;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;
import software.sava.incident.core.api.IncidentSeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/// Adapts an [IncidentIoClient] to the provider-neutral [IncidentClient].
///
/// incident.io severities, incident types, statuses, and custom fields are
/// workspace-specific ids, so the mapping from [IncidentSeverity] to a severity id — and
/// any default type/status/visibility/mode — is supplied at build time. The alert's
/// `summary` becomes the incident `name` and its `details` the incident `summary`;
/// [IncidentAlert#customDetails()] has no id-free incident.io equivalent and is not sent.
///
/// incident.io has no programmatic resolve here; [IncidentClient#supportsResolve()] is
/// false and [IncidentClient#resolveIncident(String)] fails the returned future with an
/// UnsupportedOperationException.
public final class IncidentIoIncidentClient implements IncidentClient {

  public static Builder build(final IncidentIoClient client) {
    return new Builder(client);
  }

  private final IncidentIoClient client;
  private final Map<IncidentSeverity, String> severityIds;
  private final String incidentTypeId;
  private final String statusId;
  private final String visibility;
  private final String mode;

  private IncidentIoIncidentClient(final IncidentIoClient client,
                                   final Map<IncidentSeverity, String> severityIds,
                                   final String incidentTypeId,
                                   final String statusId,
                                   final String visibility,
                                   final String mode) {
    this.client = client;
    this.severityIds = severityIds;
    this.incidentTypeId = incidentTypeId;
    this.statusId = statusId;
    this.visibility = visibility;
    this.mode = mode;
  }

  CreateIncidentRequest toRequest(final IncidentAlert alert) {
    final var key = alert.key();
    return CreateIncidentRequest.requestBuilder()
        .idempotencyKey(key == null || key.isBlank() ? UUID.randomUUID().toString() : key)
        .name(alert.summary())
        .summary(alert.details())
        .severityId(severityIds.get(alert.severity()))
        .incidentTypeId(incidentTypeId)
        .statusId(statusId)
        .visibility(visibility)
        .mode(mode)
        .build();
  }

  @Override
  public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
    return client.createIncident(toRequest(alert)).thenApply(response -> {
      final var incidentStatus = response.incidentStatus();
      return IncidentResponse.of(
          response.id(),
          incidentStatus == null ? response.reference() : incidentStatus.name(),
          response.permalink()
      );
    });
  }

  @Override
  public boolean supportsResolve() {
    return false;
  }

  @Override
  public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException(
        "incident.io incidents are resolved by status updates, which this client does not support."
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
    return "IncidentIoIncidentClient{client=" + client + '}';
  }

  public static final class Builder {

    private final IncidentIoClient client;
    private final Map<IncidentSeverity, String> severityIds;
    private String incidentTypeId;
    private String statusId;
    private String visibility;
    private String mode;

    private Builder(final IncidentIoClient client) {
      this.client = Objects.requireNonNull(client, "'client' is required.");
      this.severityIds = new EnumMap<>(IncidentSeverity.class);
    }

    public IncidentClient createClient() {
      return new IncidentIoIncidentClient(
          client,
          Map.copyOf(severityIds),
          incidentTypeId,
          statusId,
          visibility,
          mode
      );
    }

    public Builder severityId(final IncidentSeverity severity, final String severityId) {
      severityIds.put(severity, severityId);
      return this;
    }

    public Builder severityIds(final Map<IncidentSeverity, String> severityIds) {
      this.severityIds.putAll(severityIds);
      return this;
    }

    public Builder incidentTypeId(final String incidentTypeId) {
      this.incidentTypeId = incidentTypeId;
      return this;
    }

    public Builder statusId(final String statusId) {
      this.statusId = statusId;
      return this;
    }

    public Builder visibility(final CreateIncidentRequest.Visibility visibility) {
      this.visibility = visibility == null ? null : visibility.toString();
      return this;
    }

    public Builder mode(final CreateIncidentRequest.Mode mode) {
      this.mode = mode == null ? null : mode.name();
      return this;
    }
  }
}
