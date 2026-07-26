package software.sava.incident.core.api;

import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

/// Creates a provider's [IncidentClient] from configuration. Provider modules register an
/// implementation as a `java.util.ServiceLoader` service, which [IncidentClients] uses to
/// resolve the `provider` config value; see [IncidentClients] for the config formats.
///
/// Implementations must be public with a public no-arg constructor.
public interface IncidentClientFactory {

  /// Canonical provider id, e.g. `pagerduty` or `incident.io`. Matched against the
  /// `provider` config value ignoring case and any characters other than letters and
  /// digits, so `incident.io`, `incident-io`, and `IncidentIO` are equivalent.
  String provider();

  /// Creates a client from this provider's config properties, e.g. the properties
  /// documented by `PagerDutyConfig` or `IncidentIoConfig`, keyed under `prefix`.
  IncidentClient createClient(final Properties properties, final String prefix);

  default IncidentClient createClient(final Properties properties) {
    return createClient(properties, null);
  }

  /// Creates a client from this provider's JSON config object, positioned at the start of
  /// the object.
  IncidentClient createClient(final JsonIterator ji);
}
