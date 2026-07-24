package software.sava.incident.core.api;

import software.sava.incident.core.client.HttpApiClient;

import java.util.concurrent.CompletableFuture;

/// Provider-neutral incident reporting. Service-level code written against this interface
/// can switch between providers (PagerDuty, incident.io, ...) via configuration; each
/// provider module supplies an adapter from its native client.
public interface IncidentClient extends HttpApiClient {

  CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert);

  /// Whether [#resolveIncident(String)] is supported. Providers without a programmatic
  /// resolve fail the returned future with an UnsupportedOperationException.
  boolean supportsResolve();

  /// Resolves the incident correlated by `key` — the [IncidentResponse#key()] returned
  /// from [#reportIncident(IncidentAlert)], or the [IncidentAlert#key()] it was reported
  /// with.
  CompletableFuture<IncidentResponse> resolveIncident(final String key);
}
