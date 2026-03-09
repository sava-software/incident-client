package software.sava.incident.pagerduty.event.data;

import java.time.ZonedDateTime;
import java.util.*;

import static java.time.ZoneOffset.UTC;

final class PagerDutyEventPayloadBuilder
    extends PagerDutyChangeEventPayloadRecord.PagerDutyChangeEventPayloadBuilder
    implements PagerDutyEventPayload.Builder {

  private String dedupKey;
  private PagerDutySeverity severity;
  private String component;
  private String group;
  private String type;

  PagerDutyEventPayloadBuilder() {
    super();
  }

  PagerDutyEventPayloadBuilder(final PagerDutyEventPayload prototype) {
    super(prototype);
    this.dedupKey(prototype.dedupKey());
    this.severity(prototype.severity());
    this.component(prototype.component());
    this.group(prototype.group());
    this.eventClass(prototype.eventClass());
  }

  @Override
  public PagerDutyEventPayload create() {
    Objects.requireNonNull(summary, "'Summary' is a required payload field.");
    Objects.requireNonNull(source, "'Source' is a required payload field.");
    Objects.requireNonNull(severity, "'Severity' is a required payload field.");
    if (dedupKey == null || dedupKey.isBlank()) {
      dedupKey = UUID.randomUUID().toString();
    }
    if (timestamp == null) {
      timestamp = ZonedDateTime.now(UTC);
    }
    final var json = payloadJson();
    return new PagerDutyEventPayloadRecord(
        dedupKey,
        summary,
        source,
        severity,
        timestamp,
        component, group, type,
        customDetails.size() > 1 ? Collections.unmodifiableMap(customDetails) : customDetails,
        links.size() > 1 ? Collections.unmodifiableList(links) : links,
        images.size() > 1 ? Collections.unmodifiableList(images) : images,
        json);
  }

  @Override
  public PagerDutyEventPayload.Builder dedupKey(final String dedupKey) {
    if (dedupKey != null && dedupKey.length() > 255) {
      throw new IllegalArgumentException("Max length for 'dedup_key' is 255");
    }
    this.dedupKey = dedupKey;
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder summary(final String summary) {
    super.summary(summary);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder source(final String source) {
    super.source(source);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder severity(final PagerDutySeverity severity) {
    this.severity = severity;
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder timestamp(final ZonedDateTime timestamp) {
    super.timestamp(timestamp);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder component(final String component) {
    this.component = component;
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder group(final String group) {
    this.group = group;
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder eventClass(final String type) {
    this.type = type;
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder customDetails(final String field, final String fieldValue) {
    super.customDetailsObject(field, fieldValue);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder customDetails(final String field, final Boolean fieldValue) {
    super.customDetailsObject(field, fieldValue);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder customDetails(final String field, final Number fieldValue) {
    super.customDetailsObject(field, fieldValue);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder customDetails(final String field, final Object fieldValue) {
    super.customDetailsObject(field, fieldValue);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder link(final PagerDutyLinkRef link) {
    super.link(link);
    return this;
  }

  @Override
  public PagerDutyEventPayload.Builder image(final PagerDutyImageRef image) {
    super.image(image);
    return this;
  }

  @Override
  public String dedupKey() {
    return dedupKey;
  }

  @Override
  public PagerDutySeverity severity() {
    return severity;
  }

  @Override
  public String component() {
    return component;
  }

  @Override
  public String group() {
    return group;
  }

  @Override
  public String eventClass() {
    return type;
  }

  @Override
  public String payloadJson() {
    final var jsonBuilder = new StringBuilder(2_048);
    jsonBuilder.append(String.format("""
            {"summary":"%s","source":"%s","severity":"%s","timestamp":"%s\"""",
        summary, source, severity, timestamp));
    appendString(jsonBuilder, "component", component);
    appendString(jsonBuilder, "group", group);
    appendString(jsonBuilder, "class", type);
    if (!customDetails.isEmpty()) {
      jsonBuilder.append("""
          ,"custom_details":""");
      jsonBuilder.append(toJson(customDetails));
    }
    return jsonBuilder.append('}').toString();
  }
}
