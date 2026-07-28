package software.sava.incident.io;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;
import software.sava.incident.core.api.IncidentSeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/// Adapts an [IncidentIoClient] to the provider-neutral [IncidentClient].
///
/// incident.io severities, incident types, statuses, and custom fields are
/// workspace-specific ids, so the mapping from [IncidentSeverity] to a severity id — and
/// any default type/status/visibility/mode — is supplied at build time. The alert's
/// `summary` becomes the incident `name` and its `details` the incident `summary`.
///
/// [IncidentAlert#source()] and [IncidentAlert#customDetails()] are appended to that
/// incident `summary` as text. An incident.io custom field is a workspace schema object
/// referenced by id — not an arbitrary key/value like PagerDuty's `custom_details` — so
/// there is no structured place to put them without a per-key id mapping; appending
/// keeps them attached to the incident instead of dropping them.
///
/// [IncidentAlert#timestamp()] likewise needs an id to land on — incident.io timestamps
/// are workspace-specific — so it is sent only when
/// [Builder#incidentTimestampId(String)] names the incident timestamp to set. Without
/// one the alert timestamp is dropped, since incident.io stamps its own `created_at`
/// regardless.
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
  private final String incidentTimestampId;

  private IncidentIoIncidentClient(final IncidentIoClient client,
                                   final Map<IncidentSeverity, String> severityIds,
                                   final String incidentTypeId,
                                   final String statusId,
                                   final String visibility,
                                   final String mode,
                                   final String incidentTimestampId) {
    this.client = client;
    this.severityIds = severityIds;
    this.incidentTypeId = incidentTypeId;
    this.statusId = statusId;
    this.visibility = visibility;
    this.mode = mode;
    this.incidentTimestampId = incidentTimestampId;
  }

  CreateIncidentRequest toRequest(final IncidentAlert alert) {
    final var key = alert.key();
    final var builder = CreateIncidentRequest.requestBuilder()
        .idempotencyKey(key == null || key.isBlank() ? UUID.randomUUID().toString() : key)
        .name(alert.summary())
        .summary(summaryOf(alert))
        .severityId(severityIds.get(alert.severity()))
        .incidentTypeId(incidentTypeId)
        .statusId(statusId)
        .visibility(visibility)
        .mode(mode);
    // the builder normalizes a blank id to null, so this pairs an id with a timestamp
    final var timestamp = alert.timestamp();
    if (incidentTimestampId != null && timestamp != null) {
      // a region zone would render an invalid [Area/City] suffix; RFC 3339 needs the offset
      builder.incidentTimestampValues(List.of(new CreateIncidentRequest.IncidentTimestampValue(
          incidentTimestampId, timestamp.toOffsetDateTime()
      )));
    }
    return builder.build();
  }

  /// The alert's `details` become the incident `summary`, with `source` and
  /// `customDetails` appended to it.
  ///
  /// Neither has an id-free incident.io equivalent — a custom field is a workspace schema
  /// object referenced by id, not an arbitrary key/value like PagerDuty's
  /// `custom_details` — so the choice is prose or nothing, and prose keeps the operator's
  /// context attached to the incident. This is the fallback `WebhookFormats.SLACK_TEXT`
  /// already uses for the same reason.
  ///
  /// Returns the details unchanged when there is nothing to append, so an alert without
  /// either serializes exactly as before.
  private static String summaryOf(final IncidentAlert alert) {
    final var appendix = new StringBuilder(192);
    final var source = alert.source();
    if (source != null && !source.isBlank()) {
      appendix.append("Source: ").append(source);
    }
    // non-null and insertion-ordered, per IncidentAlertRecord
    alert.customDetails().forEach((field, value) -> {
      if (!appendix.isEmpty()) {
        appendix.append('\n');
      }
      appendix.append(field).append(": ").append(value);
    });
    final var details = alert.details();
    if (appendix.isEmpty()) {
      return details;
    }
    // a blank details line would leave the appendix with a leading empty line
    return details == null || details.isBlank()
        ? appendix.toString()
        : details + "\n\n" + appendix;
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
    private String incidentTimestampId;

    private Builder(final IncidentIoClient client) {
      this.client = Objects.requireNonNull(client, "'client' is required.");
      this.severityIds = new EnumMap<>(IncidentSeverity.class);
    }

    public IncidentClient createClient() {
      // the API rejects create requests without a visibility
      Objects.requireNonNull(visibility, "'visibility' is required.");
      return new IncidentIoIncidentClient(
          client,
          Map.copyOf(severityIds),
          incidentTypeId,
          statusId,
          visibility,
          mode,
          incidentTimestampId
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

    /// Workspace-specific id of the incident timestamp [IncidentAlert#timestamp()] should
    /// set. Unset, the alert timestamp is not sent at all.
    ///
    /// A blank id is normalized to unset: unlike the optional ids above,
    /// `incident_timestamp_id` is required by the payload and so is serialized even when
    /// blank — which would turn a misconfigured id into a rejected request rather than a
    /// missing timestamp.
    public Builder incidentTimestampId(final String incidentTimestampId) {
      this.incidentTimestampId = incidentTimestampId == null || incidentTimestampId.isBlank()
          ? null
          : incidentTimestampId;
      return this;
    }
  }
}
