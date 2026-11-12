package software.sava.incident.io.alert;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// An HTTP alert-source event: the body of `POST /v2/alert_events/http/{alert_source_config_id}`
/// (`Alert Events V2#CreateHTTP`), whose shape is `AlertEventsCreateHTTPPayloadV2`.
///
/// An alert source deduplicates on [#deduplicationKey()]: events with the same key update
/// one alert, and a [Status#resolved] event with the firing event's key resolves it. The
/// schema lists the key as optional but documents it as required for newly created HTTP
/// sources, so every event built here carries one.
///
/// [#metadata()] is the free-form object the alert source is configured to parse. Values are
/// modelled one level deep: a scalar, or a collection of scalars sent as a JSON array. Nested
/// objects, nested arrays and Java arrays are not modelled and are refused; see
/// [Builder#metadata(String, Object)].
public interface AlertEvent {

  static Builder build() {
    return new Builder();
  }

  /// Required; never blank.
  String title();

  /// Required.
  Status status();

  /// Never blank: a firing event without one is given a random UUID.
  String deduplicationKey();

  /// Markdown detail for the title; null when omitted.
  String description();

  /// Link to the alert in the upstream system; null when omitted.
  String sourceUrl();

  /// Insertion-ordered and unmodifiable; null values are allowed and sent as JSON `null`.
  Map<String, Object> metadata();

  /// The JSON document sent as the request body, computed once at [Builder#create()].
  String body();

  /// The alert's state, serialized by [#name()].
  enum Status {
    firing,
    resolved
  }

  /// Collects an event's fields; [#create()] validates them and computes the JSON body.
  /// Each `create()` reads the builder's current state and does not change it.
  final class Builder {

    private String title;
    private Status status;
    private String deduplicationKey;
    private String description;
    private String sourceUrl;
    private final Map<String, Object> metadata;

    private Builder() {
      this.metadata = new LinkedHashMap<>();
    }

    /// Creates the event.
    ///
    /// A firing event with a null or blank deduplication key is given a random UUID, since
    /// the response and a later resolve need one; each `create()` draws a new one. A resolved
    /// event must carry the key of the alert it resolves.
    ///
    /// @throws NullPointerException     if `title` or `status` is unset
    /// @throws IllegalArgumentException if `title` is blank, or `status` is
    ///                                  [Status#resolved] and `deduplicationKey` is null or
    ///                                  blank
    public AlertEvent create() {
      Objects.requireNonNull(title, "'title' is a required alert event field.");
      if (title.isBlank()) {
        throw new IllegalArgumentException("'title' is a required alert event field.");
      }
      Objects.requireNonNull(status, "'status' is a required alert event field.");
      final String key;
      if (deduplicationKey != null && !deduplicationKey.isBlank()) {
        key = deduplicationKey;
      } else if (status == Status.firing) {
        key = UUID.randomUUID().toString();
      } else {
        throw new IllegalArgumentException(
            "A resolved alert event requires the 'deduplicationKey' of the alert it resolves."
        );
      }
      return AlertEventRecord.create(title, status, key, description, sourceUrl, metadata);
    }

    public Builder title(final String title) {
      this.title = title;
      return this;
    }

    public Builder status(final Status status) {
      this.status = status;
      return this;
    }

    public Builder deduplicationKey(final String deduplicationKey) {
      this.deduplicationKey = deduplicationKey;
      return this;
    }

    /// Markdown; omitted when null or blank.
    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    /// Omitted when null or blank.
    public Builder sourceUrl(final String sourceUrl) {
      this.sourceUrl = sourceUrl;
      return this;
    }

    /// Adds, or replaces in place, one metadata entry.
    ///
    /// A `String` is sent as a JSON string; a `Boolean` as a literal; a `Byte`, `Short`,
    /// `Integer`, `Long` or `BigInteger` as a number; a `BigDecimal` as a number in its
    /// [java.math.BigDecimal#toPlainString()] form; a finite `Double` or `Float` as a number;
    /// null as JSON `null`; a `Collection` of such scalars as a JSON array in iteration
    /// order, snapshotted here; any other object, including any other `Number`, as its
    /// `toString()` in a JSON string. Only one level of array is modelled, and nested objects
    /// are not.
    ///
    /// @throws NullPointerException     if `key` is null
    /// @throws IllegalArgumentException if `value` is a `Map`, a Java array, a nonfinite
    ///                                  `Double` or `Float`, or a `Collection` holding any
    ///                                  of those or another `Collection`
    public Builder metadata(final String key, final Object value) {
      Objects.requireNonNull(key, "A metadata key must not be null.");
      metadata.put(key, AlertEventRecord.metadataValue(key, value));
      return this;
    }

    /// Adds every entry of `metadata` in its iteration order, as
    /// [#metadata(String, Object)] adds one.
    public Builder metadata(final Map<String, ?> metadata) {
      metadata.forEach(this::metadata);
      return this;
    }

    public String title() {
      return title;
    }

    public Status status() {
      return status;
    }

    public String deduplicationKey() {
      return deduplicationKey;
    }

    public String description() {
      return description;
    }

    public String sourceUrl() {
      return sourceUrl;
    }
  }
}
