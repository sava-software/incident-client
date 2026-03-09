package software.sava.incident.pagerduty.event.data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

record PagerDutyEventPayloadRecord(String dedupKey,
                                   String summary,
                                   String source,
                                   PagerDutySeverity severity,
                                   ZonedDateTime timestamp,
                                   String component,
                                   String group,
                                   String type,
                                   Map<String, Object> customDetails,
                                   List<PagerDutyLinkRef> links,
                                   List<PagerDutyImageRef> images,
                                   String json) implements PagerDutyEventPayload {

  @Override
  public String dedupKey() {
    return dedupKey;
  }

  @Override
  public String summary() {
    return summary;
  }

  @Override
  public String source() {
    return source;
  }

  @Override
  public PagerDutySeverity severity() {
    return severity;
  }

  @Override
  public ZonedDateTime timestamp() {
    return timestamp;
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
  public Map<String, Object> customDetails() {
    return customDetails;
  }

  @Override
  public List<PagerDutyLinkRef> links() {
    return links;
  }

  @Override
  public List<PagerDutyImageRef> images() {
    return images;
  }

  @Override
  public String payloadJson() {
    return json;
  }
}
