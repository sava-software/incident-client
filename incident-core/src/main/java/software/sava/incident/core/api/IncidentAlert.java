package software.sava.incident.core.api;

import java.time.ZonedDateTime;
import java.util.Map;

/// A provider-neutral incident report. Providers map these fields onto their own wire
/// formats; provider-specific features (PagerDuty links/images/change events, incident.io
/// custom fields and role assignments) remain on the provider clients.
public interface IncidentAlert {

  static Builder build() {
    return new IncidentAlertRecord.IncidentAlertBuilder();
  }

  static Builder build(final IncidentAlert prototype) {
    return prototype == null ? build() : new IncidentAlertRecord.IncidentAlertBuilder(prototype);
  }

  /// Correlation key: PagerDuty `dedup_key` / incident.io `idempotency_key`. May be null,
  /// in which case providers that require one generate it.
  String key();

  /// Short, single-line title. Required.
  String summary();

  /// Long-form description. Optional.
  String details();

  IncidentSeverity severity();

  /// The system the incident originates from, preferably a hostname or FQDN. Optional.
  String source();

  /// When the reporting tool detected the incident. Optional; providers that require a
  /// timestamp default to now.
  ZonedDateTime timestamp();

  Map<String, Object> customDetails();

  interface Builder extends IncidentAlert {

    IncidentAlert create();

    Builder key(final String key);

    Builder summary(final String summary);

    Builder details(final String details);

    Builder severity(final IncidentSeverity severity);

    Builder source(final String source);

    Builder timestamp(final ZonedDateTime timestamp);

    Builder customDetail(final String field, final Object fieldValue);
  }
}
