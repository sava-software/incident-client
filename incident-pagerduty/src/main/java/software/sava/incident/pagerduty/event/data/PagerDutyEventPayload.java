package software.sava.incident.pagerduty.event.data;

import java.time.ZonedDateTime;
import java.util.List;

public interface PagerDutyEventPayload extends PagerDutyChangeEventPayload {

  static Builder build() {
    return new PagerDutyEventPayloadBuilder();
  }

  static Builder build(final PagerDutyEventPayload prototype) {
    return prototype == null ? build() : new PagerDutyEventPayloadBuilder(prototype);
  }

  String dedupKey();

  PagerDutySeverity severity();

  String component();

  String group();

  String eventClass();

  List<PagerDutyImageRef> images();

  interface Builder extends PagerDutyEventPayload, PagerDutyChangeEventPayload.Builder {

    PagerDutyEventPayload create();

    PagerDutyEventPayload.Builder dedupKey(final String dedupKey);

    PagerDutyEventPayload.Builder summary(final String summary);

    PagerDutyEventPayload.Builder source(final String source);

    PagerDutyEventPayload.Builder severity(final PagerDutySeverity severity);

    PagerDutyEventPayload.Builder timestamp(final ZonedDateTime timestamp);

    PagerDutyEventPayload.Builder component(final String component);

    PagerDutyEventPayload.Builder group(final String group);

    PagerDutyEventPayload.Builder eventClass(final String type);

    PagerDutyEventPayload.Builder customDetails(final String field, final String fieldValue);

    PagerDutyEventPayload.Builder customDetails(final String field, final Boolean fieldValue);

    PagerDutyEventPayload.Builder customDetails(final String field, final Number fieldValue);

    PagerDutyEventPayload.Builder customDetails(final String field, final Object fieldValue);

    PagerDutyEventPayload.Builder link(final PagerDutyLinkRef link);

    PagerDutyEventPayload.Builder image(final PagerDutyImageRef image);
  }
}
