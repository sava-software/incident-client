package software.sava.incident.pagerduty.event.data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface PagerDutyChangeEventPayload {

  static Builder build() {
    return new PagerDutyChangeEventPayloadRecord.PagerDutyChangeEventPayloadBuilder();
  }

  static Builder build(final PagerDutyChangeEventPayload prototype) {
    return prototype == null ? build() : new PagerDutyChangeEventPayloadRecord.PagerDutyChangeEventPayloadBuilder(prototype);
  }

  ZonedDateTime timestamp();

  String summary();

  String source();

  Map<String, Object> customDetails();

  List<PagerDutyLinkRef> links();

  default List<PagerDutyImageRef> images() {
    return List.of();
  }

  default String linksJson() {
    final var links = links();
    return links.isEmpty() ? "" : links.stream().map(PagerDutyLinkRef::toJson)
        .collect(Collectors.joining(",", ",\"links\":[", "]"));
  }

  default String imagesJson() {
    final var images = images();
    return images.isEmpty() ? "" : images.stream().map(PagerDutyImageRef::toJson)
        .collect(Collectors.joining(",", ",\"images\":[", "]"));
  }

  String payloadJson();

  interface Builder extends PagerDutyChangeEventPayload {


    PagerDutyChangeEventPayload create();

    Builder summary(final String summary);

    Builder timestamp(final ZonedDateTime timestamp);

    Builder source(final String source);

    Builder customDetails(final String field, final String fieldValue);

    Builder customDetails(final String field, final Boolean fieldValue);

    Builder customDetails(final String field, final Number fieldValue);

    Builder customDetails(final String field, final Object fieldValue);

    Builder link(final PagerDutyLinkRef link);

    Builder image(final PagerDutyImageRef image);
  }
}
