package software.sava.incident.io.alert;

import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Jazzer entry point for the [AlertEvent] body serialization (`AlertEventsCreateHTTPPayloadV2`).
/// Field values and metadata keys are arbitrary caller-supplied strings, so the body must
/// always be valid JSON: no raw control character may survive escaping, and parsing the body
/// back must yield exactly the values given to the builder. Every string goes through full
/// RFC 8259 escaping (the description is markdown, so newlines survive as `\n`); blank
/// optional members are omitted, and an empty metadata object is omitted.
///
/// The leading byte selects the status (even: `firing`, odd: `resolved`); the rest is split
/// on NUL bytes into title, deduplication key, description, source URL, and then metadata
/// key/value pairs.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-io:fuzzAlertEvent [-PmaxFuzzTime=<seconds>]`.
public final class AlertEventFuzz {

  private static final String DELIMITER = String.valueOf((char) 0);

  public static void fuzzerTestOneInput(final byte[] data) {
    if (data.length == 0) {
      return;
    }
    final var status = (data[0] & 1) == 0 ? AlertEvent.Status.firing : AlertEvent.Status.resolved;
    final var parts = new String(data, 1, data.length - 1, UTF_8).split(DELIMITER, -1);
    // create() refuses a blank title, and refuses (resolved) or randomizes (firing) a blank
    // key: preconditions of building an event, not what this target explores, so a blank
    // part falls back to a fixed value and the round trip stays deterministic
    final var title = required(part(parts, 0), "fuzz-title");
    final var key = required(part(parts, 1), "fuzz-deduplication-key");
    final var description = part(parts, 2);
    final var sourceUrl = part(parts, 3);
    final var builder = AlertEvent.build()
        .status(status)
        .title(title)
        .deduplicationKey(key)
        .description(description)
        .sourceUrl(sourceUrl);
    final var expectedMetadata = new LinkedHashMap<String, Object>();
    for (int i = 4; i + 1 < parts.length; i += 2) {
      builder.metadata(parts[i], parts[i + 1]);
      expectedMetadata.put(parts[i], parts[i + 1]);
    }
    final var event = builder.create();
    final var body = event.body();
    assertNoRawControlChars(body);
    assertEq(body, event.toString(), "toString");
    assertEq(new ArrayList<>(expectedMetadata.entrySet()), new ArrayList<>(event.metadata().entrySet()), "metadata()");

    final var p = new Object() {
      String title, status, key, description, sourceUrl;
      Map<String, Object> metadata;
    };
    JsonIterator.parse(body).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("title", buf, offset, len)) {
        p.title = ji.readString();
      } else if (fieldEquals("status", buf, offset, len)) {
        p.status = ji.readString();
      } else if (fieldEquals("deduplication_key", buf, offset, len)) {
        p.key = ji.readString();
      } else if (fieldEquals("description", buf, offset, len)) {
        p.description = ji.readString();
      } else if (fieldEquals("source_url", buf, offset, len)) {
        p.sourceUrl = ji.readString();
      } else if (fieldEquals("metadata", buf, offset, len)) {
        p.metadata = new LinkedHashMap<>();
        ji.testObject((buf2, offset2, len2, ji2) -> {
          final var metadataKey = new String(buf2, offset2, len2);
          if (p.metadata.containsKey(metadataKey)) {
            throw new AssertionError("duplicate metadata key '" + metadataKey + "' in: " + body);
          }
          p.metadata.put(metadataKey, ji2.readString());
          return true;
        });
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });

    assertEq(title, p.title, "title");
    assertEq(status.name(), p.status, "status");
    assertEq(key, p.key, "deduplication_key");
    assertOptionalField(description, p.description, "description");
    assertOptionalField(sourceUrl, p.sourceUrl, "source_url");
    if (expectedMetadata.isEmpty()) {
      assertEq(null, p.metadata, "metadata");
    } else {
      assertEq(new ArrayList<>(expectedMetadata.entrySet()), new ArrayList<>(p.metadata.entrySet()), "metadata");
    }
  }

  private static String part(final String[] parts, final int i) {
    return i < parts.length ? parts[i] : null;
  }

  /// A required field: the fuzzed value when it is usable, else `fallback`.
  private static String required(final String value, final String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static void assertNoRawControlChars(final String json) {
    for (int i = 0; i < json.length(); ++i) {
      final char c = json.charAt(i);
      if (c < 0x20) {
        throw new AssertionError("raw control character 0x" + Integer.toHexString(c) + " in: " + json);
      }
    }
  }

  private static void assertOptionalField(final String expected, final String parsed, final String field) {
    if (expected != null && !expected.isBlank()) {
      assertEq(expected, parsed, field);
    } else if (parsed != null) {
      throw new AssertionError(field + " should have been omitted, parsed '" + parsed + '\'');
    }
  }

  private static void assertEq(final Object expected, final Object parsed, final String field) {
    if (!Objects.equals(expected, parsed)) {
      throw new AssertionError(field + " did not round-trip: expected '" + expected + "', parsed '" + parsed + '\'');
    }
  }

  private AlertEventFuzz() {
  }
}
