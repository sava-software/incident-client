package software.sava.incident.io.alert;

import software.sava.incident.core.client.HttpApiClient;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/// Sends [AlertEvent]s to an incident.io HTTP alert source:
/// `POST /v2/alert_events/http/{alert_source_config_id}` (`Alert Events V2#CreateHTTP`).
///
/// incident.io recommends this route over Create Incident for monitor signals: the alert
/// source deduplicates on the event's key and resolves with the same key, and the
/// workspace's alert routes decide whether and how an alert becomes an incident.
///
/// The request authenticates with the alert source's own secret, sent as
/// `Authorization: Bearer <token>` (see [Builder#token(String)]), not with an API key. A
/// non-2xx response fails the future with an
/// [software.sava.incident.io.exceptions.IncidentIoRequestException]; a 2xx body that cannot
/// be parsed with an [software.sava.incident.io.exceptions.IncidentIoParseException], which
/// is not retryable.
public interface IncidentIoAlertEventClient extends HttpApiClient {

  static Builder clientBuilder() {
    return new Builder();
  }

  /// Posts `event`'s [AlertEvent#body()] with the client's request timeout.
  ///
  /// @throws NullPointerException if `event` is null, before anything is sent
  CompletableFuture<AlertEventResponse> sendAlertEvent(final AlertEvent event);

  /// Starts a builder for the provider-neutral `IncidentClient` adapter over this client;
  /// see [IncidentIoAlertIncidentClient].
  default IncidentIoAlertIncidentClient.Builder incidentClientBuilder() {
    return IncidentIoAlertIncidentClient.build(this);
  }

  final class Builder extends HttpApiClient.Builder<Builder> {

    static final String DEFAULT_ENDPOINT_PREFIX = "https://api.incident.io/v2/alert_events/http/";

    private String alertSourceConfigId;

    private Builder() {
    }

    /// Creates the client. With an [#endpoint(URI)] set, that is the full request URL, used
    /// as given, and the alert source config id is ignored. Otherwise the URL is
    /// `https://api.incident.io/v2/alert_events/http/<id>`, the id's UTF-8 bytes
    /// percent-encoded as one RFC 3986 path segment.
    ///
    /// @throws NullPointerException     if no endpoint is set and the id is null
    /// @throws IllegalArgumentException if no endpoint is set and the id is blank
    public IncidentIoAlertEventClient createClient() {
      setDefaults();
      final URI uri;
      if (endpoint == null) {
        Objects.requireNonNull(alertSourceConfigId, "'alertSourceConfigId' is required.");
        if (alertSourceConfigId.isBlank()) {
          throw new IllegalArgumentException("'alertSourceConfigId' is required.");
        }
        uri = URI.create(DEFAULT_ENDPOINT_PREFIX + IncidentIoAlertEventClientImpl.encodePathSegment(alertSourceConfigId));
      } else {
        uri = endpoint;
      }
      return new IncidentIoAlertEventClientImpl(
          uri,
          httpClient,
          requestTimeout,
          extendRequest,
          testResponse
      );
    }

    /// The id of the HTTP alert source config, shown on the alert source in incident.io.
    public Builder alertSourceConfigId(final String alertSourceConfigId) {
      this.alertSourceConfigId = alertSourceConfigId;
      return this;
    }

    public String alertSourceConfigId() {
      return alertSourceConfigId;
    }

    /// The alert source's secret: sets `Authorization: Bearer <token>` and
    /// `Content-Type: application/json` on every request, replacing any
    /// [#extendRequest(java.util.function.UnaryOperator)] set before.
    public Builder token(final String token) {
      return extendRequest(builder -> {
        builder.setHeader("Authorization", "Bearer " + token);
        builder.setHeader("Content-Type", "application/json");
        return builder;
      });
    }
  }
}
