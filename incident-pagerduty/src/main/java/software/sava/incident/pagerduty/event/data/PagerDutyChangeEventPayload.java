package software.sava.incident.pagerduty.event.data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface PagerDutyChangeEventPayload {

  static Builder build() {
    return new PagerDutyChangeEventPayloadRecord.PagerDutyChangeEventPayloadBuilder();
  }

  /// A builder seeded from `prototype`, or an empty one when it is null. The prototype's
  /// links and images are added one by one as [Builder#link] and [Builder#image] add them,
  /// so they are validated and any ref builder among them is created.
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
        .collect(Collectors.joining(",", """
            ,"links":[""", "]"));
  }

  default String imagesJson() {
    final var images = images();
    return images.isEmpty() ? "" : images.stream().map(PagerDutyImageRef::toJson)
        .collect(Collectors.joining(",", """
            ,"images":[""", "]"));
  }

  String payloadJson();

  interface Builder extends PagerDutyChangeEventPayload {

    /// Creates a payload with immutable snapshots of the custom-details map and
    /// attachment lists. The contained custom-detail values are not copied. Attachments
    /// were normalized when added (see [#link] and [#image]): a link or image builder was
    /// created then, so later changes to it do not reach this payload; any other attachment
    /// object is held as given. A null timestamp is set to the current time on this builder,
    /// so a later `create()` repeats it.
    ///
    /// @throws NullPointerException     if `summary` is unset
    /// @throws IllegalArgumentException if a numeric custom detail is a nonfinite
    ///                                  `Double` or `Float`
    PagerDutyChangeEventPayload create();

    Builder summary(final String summary);

    Builder timestamp(final ZonedDateTime timestamp);

    Builder source(final String source);

    Builder customDetails(final String field, final String fieldValue);

    Builder customDetails(final String field, final Boolean fieldValue);

    Builder customDetails(final String field, final Number fieldValue);

    Builder customDetails(final String field, final Object fieldValue);

    /// Adds a link. A [PagerDutyLinkRef.Builder] is created here and the created link is
    /// stored, so later changes to the builder do not reach this payload. The link the
    /// payload keeps must have an `href`, as the Events API v2 schema requires.
    ///
    /// @throws NullPointerException     if `link` or its `href` is null
    /// @throws IllegalArgumentException if its `href` is blank
    Builder link(final PagerDutyLinkRef link);

    /// Adds an image. A [PagerDutyImageRef.Builder] is created here and the created image is
    /// stored, so later changes to the builder do not reach this payload. The image the
    /// payload keeps must have a `src`, as the Events API v2 schema requires.
    ///
    /// @throws NullPointerException     if `image` or its `src` is null
    /// @throws IllegalArgumentException if its `src` is blank
    Builder image(final PagerDutyImageRef image);
  }
}
