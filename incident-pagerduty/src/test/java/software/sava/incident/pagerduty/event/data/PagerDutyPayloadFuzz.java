package software.sava.incident.pagerduty.event.data;

import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Jazzer entry point for the PagerDuty event and change-event payload serialization.
/// Builder inputs are arbitrary caller-supplied strings, so the serialized event must
/// always be valid JSON: no raw control character may survive escaping, and parsing the
/// event back must yield the builder's values modulo the documented transformations —
/// summaries truncate at 1024 characters, and line feeds/carriage returns are removed
/// from string fields.
///
/// The input is split on NUL bytes into field values; the leading byte selects the
/// severity.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-pagerduty:fuzzPayload [-PmaxFuzzTime=<seconds>]`.
public final class PagerDutyPayloadFuzz {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, UTC);
  private static final String DELIMITER = String.valueOf((char) 0);

  public static void fuzzerTestOneInput(final byte[] data) {
    if (data.length == 0) {
      return;
    }
    final var parts = new String(data, 1, data.length - 1, UTF_8).split(DELIMITER, -1);
    final var summary = part(parts, 0);
    if (summary == null || summary.isBlank()) {
      return; // the builder requires a non-blank summary
    }
    final var source = part(parts, 1) == null || parts[1].isEmpty() ? "fuzz-source" : parts[1];
    final var severities = PagerDutySeverity.values();
    final var severity = severities[(data[0] & 0xFF) % severities.length];
    final var builder = PagerDutyEventPayload.build()
        .dedupKey("fuzz-dedup-key")
        .summary(summary)
        .source(source)
        .severity(severity)
        .timestamp(TIMESTAMP)
        .component(part(parts, 2))
        .group(part(parts, 3))
        .eventClass(part(parts, 4));
    final var expectedDetails = new LinkedHashMap<String, Object>();
    if (parts.length > 6) {
      builder.customDetails(parts[5], parts[6]);
      expectedDetails.put(strip(parts[5]), strip(parts[6]));
    }
    builder.customDetails("fuzz-num", 42);
    expectedDetails.put("fuzz-num", 42);
    final var linkHref = part(parts, 7);
    final var linkText = part(parts, 8);
    final boolean expectLink = linkHref != null && linkText != null && !linkHref.isBlank();
    if (expectLink) {
      builder.link(PagerDutyLinkRef.build().href(linkHref).text(linkText).create());
      builder.image(PagerDutyImageRef.build().src(linkHref).href(linkHref).alt(linkText).create());
    }
    final var payload = builder.create();

    final var eventJson = "{\"routing_key\":\"rk\",\"payload\":"
        + payload.payloadJson() + payload.linksJson() + payload.imagesJson() + '}';
    assertNoRawControlChars(eventJson);

    final var expectedSummary = strip(summary.length() > 1_024 ? summary.substring(0, 1_024) : summary);
    final var p = new Parsed();
    parseEvent(eventJson, p);
    assertEq(expectedSummary, p.summary, "summary");
    assertEq(strip(source), p.source, "source");
    assertEq(severity.name(), p.severity, "severity");
    assertEq(TIMESTAMP.toString(), p.timestamp, "timestamp");
    assertOptionalField(part(parts, 2), p.component, "component");
    assertOptionalField(part(parts, 3), p.group, "group");
    assertOptionalField(part(parts, 4), p.eventClass, "class");
    assertEq(expectedDetails, p.customDetails, "custom_details");
    if (expectLink) {
      assertEq(strip(linkHref), p.linkHref, "link href");
      if (linkText.isBlank()) {
        assertEq(null, p.linkText, "link text");
      } else {
        assertEq(strip(linkText), p.linkText, "link text");
      }
      assertEq(strip(linkHref), p.imageSrc, "image src");
      assertEq(strip(linkHref), p.imageHref, "image href");
    } else {
      assertEq(null, p.linkHref, "link href");
      assertEq(null, p.imageSrc, "image src");
    }

    // the change-event payload serializes through a separate code path
    final var changePayload = PagerDutyChangeEventPayload.build(payload).create();
    final var changeJson = "{\"routing_key\":\"rk\",\"payload\":"
        + changePayload.payloadJson() + changePayload.linksJson() + changePayload.imagesJson() + '}';
    assertNoRawControlChars(changeJson);

    final var c = new Parsed();
    parseEvent(changeJson, c);
    assertEq(expectedSummary, c.summary, "change summary");
    assertOptionalField(source, c.source, "change source");
    assertEq(TIMESTAMP.toString(), c.timestamp, "change timestamp");
    assertEq(expectedDetails, c.customDetails, "change custom_details");
  }

  private static final class Parsed {

    String summary, source, severity, timestamp, component, group, eventClass;
    String linkHref, linkText, imageSrc, imageHref, imageAlt;
    Map<String, Object> customDetails;
  }

  private static void parseEvent(final String json, final Parsed p) {
    JsonIterator.parse(json).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("routing_key", buf, offset, len)) {
        ji.skip();
      } else if (fieldEquals("payload", buf, offset, len)) {
        ji.testObject((buf2, offset2, len2, ji2) -> {
          if (fieldEquals("summary", buf2, offset2, len2)) {
            p.summary = ji2.readString();
          } else if (fieldEquals("source", buf2, offset2, len2)) {
            p.source = ji2.readString();
          } else if (fieldEquals("severity", buf2, offset2, len2)) {
            p.severity = ji2.readString();
          } else if (fieldEquals("timestamp", buf2, offset2, len2)) {
            p.timestamp = ji2.readString();
          } else if (fieldEquals("component", buf2, offset2, len2)) {
            p.component = ji2.readString();
          } else if (fieldEquals("group", buf2, offset2, len2)) {
            p.group = ji2.readString();
          } else if (fieldEquals("class", buf2, offset2, len2)) {
            p.eventClass = ji2.readString();
          } else if (fieldEquals("custom_details", buf2, offset2, len2)) {
            p.customDetails = new LinkedHashMap<>();
            ji2.testObject((buf3, offset3, len3, ji3) -> {
              final var key = new String(buf3, offset3, len3);
              if (ji3.whatIsNext() == ValueType.NUMBER) {
                p.customDetails.put(key, ji3.readInt());
              } else {
                p.customDetails.put(key, ji3.readString());
              }
              return true;
            });
          } else {
            throw new AssertionError("unexpected payload field " + new String(buf2, offset2, len2));
          }
          return true;
        });
      } else if (fieldEquals("links", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("href", buf2, offset2, len2)) {
              p.linkHref = ji2.readString();
            } else if (fieldEquals("text", buf2, offset2, len2)) {
              p.linkText = ji2.readString();
            } else {
              throw new AssertionError("unexpected link field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else if (fieldEquals("images", buf, offset, len)) {
        while (ji.readArray()) {
          ji.testObject((buf2, offset2, len2, ji2) -> {
            if (fieldEquals("src", buf2, offset2, len2)) {
              p.imageSrc = ji2.readString();
            } else if (fieldEquals("href", buf2, offset2, len2)) {
              p.imageHref = ji2.readString();
            } else if (fieldEquals("alt", buf2, offset2, len2)) {
              p.imageAlt = ji2.readString();
            } else {
              throw new AssertionError("unexpected image field " + new String(buf2, offset2, len2));
            }
            return true;
          });
        }
      } else {
        throw new AssertionError("unexpected event field " + new String(buf, offset, len));
      }
      return true;
    });
  }

  private static String part(final String[] parts, final int i) {
    return i < parts.length ? parts[i] : null;
  }

  /// The expected serialization transform for string fields: line feeds and carriage
  /// returns are removed; every other escape round-trips back to the original character.
  private static String strip(final String str) {
    return str.replace("\n", "").replace("\r", "");
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
      assertEq(strip(expected), parsed, field);
    } else if (parsed != null) {
      throw new AssertionError(field + " should have been omitted, parsed '" + parsed + '\'');
    }
  }

  private static void assertEq(final Object expected, final Object parsed, final String field) {
    if (!Objects.equals(expected, parsed)) {
      throw new AssertionError(field + " did not round-trip: expected '" + expected + "', parsed '" + parsed + '\'');
    }
  }

  private PagerDutyPayloadFuzz() {
  }
}
