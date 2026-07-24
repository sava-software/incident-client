package software.sava.incident.core.api;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

record IncidentAlertRecord(String key,
                           String summary,
                           String details,
                           IncidentSeverity severity,
                           String source,
                           ZonedDateTime timestamp,
                           Map<String, Object> customDetails) implements IncidentAlert {

  static final class IncidentAlertBuilder implements Builder {

    private String key;
    private String summary;
    private String details;
    private IncidentSeverity severity;
    private String source;
    private ZonedDateTime timestamp;
    private Map<String, Object> customDetails;

    IncidentAlertBuilder() {
      this.customDetails = Map.of();
    }

    IncidentAlertBuilder(final IncidentAlert prototype) {
      this.key = prototype.key();
      this.summary = prototype.summary();
      this.details = prototype.details();
      this.severity = prototype.severity();
      this.source = prototype.source();
      this.timestamp = prototype.timestamp();
      final var customDetails = prototype.customDetails();
      this.customDetails = customDetails == null || customDetails.isEmpty()
          ? Map.of()
          : new LinkedHashMap<>(customDetails);
    }

    @Override
    public IncidentAlert create() {
      Objects.requireNonNull(summary, "'summary' is a required alert field.");
      Objects.requireNonNull(severity, "'severity' is a required alert field.");
      return new IncidentAlertRecord(
          key,
          summary,
          details,
          severity,
          source,
          timestamp,
          // keep insertion order — Map.copyOf would shuffle it
          customDetails.size() > 1
              ? Collections.unmodifiableMap(new LinkedHashMap<>(customDetails))
              : Map.copyOf(customDetails)
      );
    }

    @Override
    public Builder key(final String key) {
      this.key = key;
      return this;
    }

    @Override
    public Builder summary(final String summary) {
      this.summary = summary;
      return this;
    }

    @Override
    public Builder details(final String details) {
      this.details = details;
      return this;
    }

    @Override
    public Builder severity(final IncidentSeverity severity) {
      this.severity = severity;
      return this;
    }

    @Override
    public Builder source(final String source) {
      this.source = source;
      return this;
    }

    @Override
    public Builder timestamp(final ZonedDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    @Override
    public Builder customDetail(final String field, final Object fieldValue) {
      if (customDetails.isEmpty()) {
        customDetails = Map.of(field, fieldValue);
        return this;
      } else if (customDetails.size() == 1) {
        customDetails = new LinkedHashMap<>(customDetails);
      }
      customDetails.put(field, fieldValue);
      return this;
    }

    @Override
    public String key() {
      return key;
    }

    @Override
    public String summary() {
      return summary;
    }

    @Override
    public String details() {
      return details;
    }

    @Override
    public IncidentSeverity severity() {
      return severity;
    }

    @Override
    public String source() {
      return source;
    }

    @Override
    public ZonedDateTime timestamp() {
      return timestamp;
    }

    @Override
    public Map<String, Object> customDetails() {
      return customDetails;
    }
  }
}
