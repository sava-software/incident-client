package software.sava.incident.core.api;

import java.time.ZonedDateTime;
import java.util.Map;

/// Supplies a per-report key without copying, validating, or rendering the caller's data.
record KeyedIncidentAlert(IncidentAlert alert, String key) implements IncidentAlert {

  @Override
  public String summary() {
    return alert.summary();
  }

  @Override
  public String details() {
    return alert.details();
  }

  @Override
  public IncidentSeverity severity() {
    return alert.severity();
  }

  @Override
  public String source() {
    return alert.source();
  }

  @Override
  public ZonedDateTime timestamp() {
    return alert.timestamp();
  }

  @Override
  public Map<String, Object> customDetails() {
    return alert.customDetails();
  }

  @Override
  public String toString() {
    return alert.toString();
  }
}
