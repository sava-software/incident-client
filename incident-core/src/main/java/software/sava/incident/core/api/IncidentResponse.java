package software.sava.incident.core.api;

/// A provider-neutral view of the provider's acknowledgement of a reported incident.
public interface IncidentResponse {

  static IncidentResponse of(final String key, final String status, final String url) {
    return new IncidentResponseRecord(key, status, url);
  }

  /// The provider's correlation key for this incident: PagerDuty `dedup_key` /
  /// incident.io incident `id`. Pass to [IncidentClient#resolveIncident(String)].
  String key();

  /// Provider status string, e.g. PagerDuty `success` or an incident.io status name.
  String status();

  /// Link to the incident if the provider returns one, else null.
  String url();
}
