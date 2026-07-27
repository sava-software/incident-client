package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Jazzer entry point for the [WebhookFormats] and [TelegramTextFormat] renderers. Alert
/// fields are arbitrary caller-supplied strings, so every rendered document must always
/// be valid JSON: no raw control character may survive escaping, parsing the generic
/// body back must yield exactly the values given to the builder (blank optionals omitted
/// by contract), and the Slack text must carry every field through the documented entity
/// transform (`&`/`<`/`>` → `&amp;`/`&lt;`/`&gt;`), which is inverted and checked for
/// containment. Slack and Telegram share one plain-text renderer, so the differential is
/// exact: the entity-unescaped Slack text must equal the Telegram text (inputs here stay
/// under the Telegram truncation limit), and the Telegram chat id must round-trip.
///
/// The first byte selects the severity; the remainder is split on NUL bytes into
/// summary, key, details, source, one custom-detail entry, and the Telegram chat id.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-webhook:fuzzFormat [-PmaxFuzzTime=<seconds>]`.
public final class WebhookFormatFuzz {

  private static final String DELIMITER = String.valueOf((char) 0);
  // fixed, non-zero-origin timestamp: the renderers take the alert's clock as data
  private static final ZonedDateTime TIMESTAMP =
      ZonedDateTime.of(2026, 7, 26, 1, 2, 3, 4, ZoneOffset.UTC);
  private static final String TIMESTAMP_RFC3339 = "2026-07-26T01:02:03.000000004Z";

  public static void fuzzerTestOneInput(final byte[] data) {
    if (data.length == 0) {
      return;
    }
    final var severities = IncidentSeverity.values();
    final var severity = severities[Math.floorMod(data[0], severities.length)];
    final var parts = new String(data, 1, data.length - 1, UTF_8).split(DELIMITER, -1);
    final var summary = parts[0];
    final var key = part(parts, 1);
    final var details = part(parts, 2);
    final var source = part(parts, 3);
    final var builder = IncidentAlert.build()
        .summary(summary)
        .severity(severity)
        .key(key)
        .details(details)
        .source(source)
        .timestamp(TIMESTAMP);
    final String customKey;
    final String customValue;
    if (parts.length > 5) {
      customKey = parts[4];
      customValue = parts[5];
      builder.customDetail(customKey, customValue);
    } else {
      customKey = null;
      customValue = null;
    }
    final var alert = builder.create();

    final var genericJson = WebhookFormats.GENERIC_JSON.render(alert);
    assertNoRawControlChars(genericJson);
    final var p = new Object() {
      String summary, severity, key, source, timestamp, details, customKey, customValue;
    };
    JsonIterator.parse(genericJson).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("summary", buf, offset, len)) {
        p.summary = ji.readString();
      } else if (fieldEquals("severity", buf, offset, len)) {
        p.severity = ji.readString();
      } else if (fieldEquals("key", buf, offset, len)) {
        p.key = ji.readString();
      } else if (fieldEquals("source", buf, offset, len)) {
        p.source = ji.readString();
      } else if (fieldEquals("timestamp", buf, offset, len)) {
        p.timestamp = ji.readString();
      } else if (fieldEquals("details", buf, offset, len)) {
        p.details = ji.readString();
      } else if (fieldEquals("customDetails", buf, offset, len)) {
        ji.testObject((buf2, offset2, len2, ji2) -> {
          p.customKey = new String(buf2, offset2, len2);
          p.customValue = ji2.readString();
          return true;
        });
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });
    assertEq(summary, p.summary, "summary");
    assertEq(severity.name(), p.severity, "severity");
    assertEq(TIMESTAMP_RFC3339, p.timestamp, "timestamp");
    assertOptionalField(key, p.key, "key");
    assertOptionalField(details, p.details, "details");
    assertOptionalField(source, p.source, "source");
    if (customKey != null) {
      assertEq(customKey, p.customKey, "customDetails key");
      assertEq(customValue, p.customValue, "customDetails value");
    } else if (p.customKey != null) {
      throw new AssertionError("customDetails should have been omitted, parsed '" + p.customKey + '\'');
    }

    final var slackJson = WebhookFormats.SLACK_TEXT.render(alert);
    assertNoRawControlChars(slackJson);
    final var slack = new Object() {
      String text;
    };
    JsonIterator.parse(slackJson).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("text", buf, offset, len)) {
        slack.text = ji.readString();
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });
    final var text = slack.text;
    if (text == null) {
      throw new AssertionError("slack text missing in: " + slackJson);
    }
    // every '<' and '>' must be entity-escaped, and every '&' must head an entity
    for (int i = 0; i < text.length(); ++i) {
      final char c = text.charAt(i);
      if (c == '<' || c == '>') {
        throw new AssertionError("raw '" + c + "' in slack text: " + text);
      }
      if (c == '&'
          && !text.startsWith("&amp;", i)
          && !text.startsWith("&lt;", i)
          && !text.startsWith("&gt;", i)) {
        throw new AssertionError("raw '&' at " + i + " in slack text: " + text);
      }
    }
    // the escape is injective, so this ordering exactly inverts it
    final var unescaped = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
    assertContains(unescaped, summary, "summary");
    assertContains(unescaped, '[' + severity.name() + "] ", "severity");
    assertContains(unescaped, TIMESTAMP_RFC3339, "timestamp");
    if (key != null && !key.isBlank()) {
      assertContains(unescaped, key, "key");
    }
    if (details != null && !details.isBlank()) {
      assertContains(unescaped, details, "details");
    }
    if (source != null && !source.isBlank()) {
      assertContains(unescaped, source, "source");
    }
    if (customKey != null) {
      assertContains(unescaped, customKey + ": " + customValue, "customDetails");
    }

    // maxLen bounds the plain text well under MAX_TEXT_LENGTH, so no truncation: the
    // entity-unescaped slack text and the telegram text must be identical
    final var chatId = parts.length > 6 && !parts[6].isBlank() ? parts[6] : "chat-1";
    final var telegramJson = new TelegramTextFormat(chatId).render(alert);
    assertNoRawControlChars(telegramJson);
    final var telegram = new Object() {
      String chatId, text;
    };
    JsonIterator.parse(telegramJson).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("chat_id", buf, offset, len)) {
        telegram.chatId = ji.readString();
      } else if (fieldEquals("text", buf, offset, len)) {
        telegram.text = ji.readString();
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });
    assertEq(chatId, telegram.chatId, "chat_id");
    assertEq(unescaped, telegram.text, "telegram text vs unescaped slack text");
  }

  private static String part(final String[] parts, final int i) {
    return i < parts.length ? parts[i] : null;
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

  private static void assertContains(final String text, final String expected, final String field) {
    if (!text.contains(expected)) {
      throw new AssertionError(field + " '" + expected + "' not carried in slack text: " + text);
    }
  }

  private WebhookFormatFuzz() {
  }
}
