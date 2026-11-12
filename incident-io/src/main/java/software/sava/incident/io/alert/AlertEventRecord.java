package software.sava.incident.io.alert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static software.sava.incident.core.json.JsonUtil.escapeJson;

record AlertEventRecord(String title,
                        Status status,
                        String deduplicationKey,
                        String description,
                        String sourceUrl,
                        Map<String, Object> metadata,
                        String body) implements AlertEvent {

  /// Snapshots `metadata` and computes the body. The arguments are already validated:
  /// `title` non-blank, `status` and `deduplicationKey` set, every metadata value accepted by
  /// [#metadataValue(String, Object)].
  static AlertEventRecord create(final String title,
                                 final Status status,
                                 final String deduplicationKey,
                                 final String description,
                                 final String sourceUrl,
                                 final Map<String, Object> metadata) {
    // keeps insertion order and null values, which Map.copyOf would not
    final var snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    final var json = new StringBuilder(256);
    json.append("{\"title\":\"").append(escapeJson(title))
        .append("\",\"status\":\"").append(status.name())
        .append("\",\"deduplication_key\":\"").append(escapeJson(deduplicationKey)).append('"');
    appendOptional(json, "description", description);
    appendOptional(json, "source_url", sourceUrl);
    if (!snapshot.isEmpty()) {
      json.append(",\"metadata\":{");
      boolean first = true;
      for (final var entry : snapshot.entrySet()) {
        if (first) {
          first = false;
        } else {
          json.append(',');
        }
        json.append('"').append(escapeJson(entry.getKey())).append("\":");
        appendValue(json, entry.getValue());
      }
      json.append('}');
    }
    json.append('}');
    return new AlertEventRecord(title, status, deduplicationKey, description, sourceUrl, snapshot, json.toString());
  }

  /// Whether [AlertEvent.Builder#metadata(String, Object)] would refuse `value`: a `Map`, a
  /// Java array, a nonfinite `Double` or `Float`, or a `Collection` holding any of those or
  /// another `Collection`.
  static boolean refusesMetadataValue(final Object value) {
    if (value instanceof Collection<?> collection) {
      for (final var element : collection) {
        if (element instanceof Collection<?> || refusesScalar(element)) {
          return true;
        }
      }
      return false;
    }
    return refusesScalar(value);
  }

  private static boolean refusesScalar(final Object value) {
    return switch (value) {
      case null -> false;
      case Map<?, ?> _ -> true;
      case Double number -> !Double.isFinite(number);
      case Float number -> !Float.isFinite(number);
      default -> value.getClass().isArray();
    };
  }

  /// The value [AlertEvent.Builder#metadata(String, Object)] stores: `value` itself, or for a
  /// collection an unmodifiable snapshot of it that keeps null elements.
  static Object metadataValue(final String key, final Object value) {
    if (refusesMetadataValue(value)) {
      throw new IllegalArgumentException("Metadata '" + key
          + "' must be a finite scalar or a collection of them; nested objects and arrays are not modelled.");
    }
    return value instanceof Collection<?> collection
        ? Collections.unmodifiableList(new ArrayList<>(collection))
        : value;
  }

  private static void appendOptional(final StringBuilder json, final String field, final String value) {
    if (value != null && !value.isBlank()) {
      json.append(",\"").append(field).append("\":\"").append(escapeJson(value)).append('"');
    }
  }

  private static void appendValue(final StringBuilder json, final Object value) {
    switch (value) {
      case null -> json.append("null");
      case String string -> json.append('"').append(escapeJson(string)).append('"');
      case Boolean bool -> json.append(bool.booleanValue());
      case Number number when isBoxedPrimitive(number) -> json.append(number);
      // exact classes: a subclass may override toString with something that is not a number
      case BigInteger integer when integer.getClass() == BigInteger.class -> json.append(integer);
      case BigDecimal decimal when decimal.getClass() == BigDecimal.class -> json.append(decimal.toPlainString());
      case Collection<?> collection -> {
        json.append('[');
        boolean first = true;
        for (final var element : collection) {
          if (first) {
            first = false;
          } else {
            json.append(',');
          }
          appendValue(json, element);
        }
        json.append(']');
      }
      default -> json.append('"').append(escapeJson(value.toString())).append('"');
    }
  }

  /// The boxed primitives whose `toString()` is a JSON number; a nonfinite `Double` or `Float`
  /// never gets here, [#refusesMetadataValue(Object)] having refused it.
  private static boolean isBoxedPrimitive(final Number number) {
    return number instanceof Byte || number instanceof Short || number instanceof Integer
        || number instanceof Long || number instanceof Double || number instanceof Float;
  }

  @Override
  public String toString() {
    return body;
  }
}
