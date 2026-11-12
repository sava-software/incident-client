package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class TelegramTextFormatTests {

  @Test
  void fullAlert() {
    final var alert = IncidentAlert.build()
        .key("dedup-1")
        .summary("db \"primary\" down")
        .details("line one\nline two")
        .severity(IncidentSeverity.CRITICAL)
        .source("validator-01.example.com")
        .timestamp(ZonedDateTime.of(2026, 7, 26, 12, 30, 45, 0, ZoneOffset.UTC))
        .customDetail("slot", 350000000L)
        .create();
    assertEquals("""
            {"chat_id":"-1001234567890","text":"[CRITICAL] db \\"primary\\" down\\nline one\\nline two\\nSource: validator-01.example.com\\nTime: 2026-07-26T12:30:45Z\\nKey: dedup-1\\nslot: 350000000"}""",
        new TelegramTextFormat("-1001234567890").render(alert)
    );
  }

  /// No parse_mode is sent, so Telegram treats the text as plain: markup and Slack
  /// entity characters must pass through untouched.
  @Test
  void plainTextKeepsMarkupCharacters() {
    final var alert = IncidentAlert.build()
        .summary("a & b <tag> *bold* _it_")
        .severity(IncidentSeverity.ERROR)
        .create();
    assertEquals("""
            {"chat_id":"@channel","text":"[ERROR] a & b <tag> *bold* _it_"}""",
        new TelegramTextFormat("@channel").render(alert)
    );
  }

  @Test
  void chatIdIsEscaped() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .severity(IncidentSeverity.INFO)
        .create();
    assertEquals("""
            {"chat_id":"a\\"b","text":"[INFO] summary-1"}""",
        new TelegramTextFormat("a\"b").render(alert)
    );
  }

  @Test
  void chatIdIsRequired() {
    final var nullEx = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(null));
    assertEquals("TelegramTextFormat chatId is required.", nullEx.getMessage());
    assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(" "));
  }

  @Test
  void textTruncatesAtTheSendMessageLimit() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .details("d".repeat(TelegramTextFormat.MAX_TEXT_LENGTH + 100))
        .severity(IncidentSeverity.WARNING)
        .create();
    final var json = new TelegramTextFormat("chat-1").render(alert);

    final var parsed = new Object() {
      String chatId, text;
    };
    JsonIterator.parse(json).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("chat_id", buf, offset, len)) {
        parsed.chatId = ji.readString();
      } else if (fieldEquals("text", buf, offset, len)) {
        parsed.text = ji.readString();
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });
    assertEquals("chat-1", parsed.chatId);
    assertEquals(TelegramTextFormat.MAX_TEXT_LENGTH, parsed.text.length());
    assertTrue(parsed.text.startsWith("[WARNING] summary-1\nd"));
  }

  @Test
  void textAtTheLimitIsNotTruncated() {
    final var prefix = "[WARNING] ";
    final var summary = "s".repeat(TelegramTextFormat.MAX_TEXT_LENGTH - prefix.length());
    final var alert = IncidentAlert.build()
        .summary(summary)
        .severity(IncidentSeverity.WARNING)
        .create();
    final var json = new TelegramTextFormat("chat-1").render(alert);
    assertEquals("""
        {"chat_id":"chat-1","text":"%s%s"}""".formatted(prefix, summary), json);
  }

  private static final String PREFIX = "[WARNING] ";

  private static String renderedText(final String json) {
    final var parsed = new Object() {
      String text;
    };
    JsonIterator.parse(json).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("text", buf, offset, len)) {
        parsed.text = ji.readString();
      } else {
        ji.skip();
      }
      return true;
    });
    return parsed.text;
  }

  private static void assertStrictUtf8(final String json) {
    try {
      UTF_8.newEncoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .encode(CharBuffer.wrap(json));
    } catch (final CharacterCodingException e) {
      throw new AssertionError("rendered body has no strict UTF-8 encoding", e);
    }
  }

  /// U+1F6A8 occupies indices 4,095 and 4,096: a cut at the limit would keep only its high
  /// half, which UTF-8 cannot encode, so the cut moves back one unit.
  @Test
  void truncationNeverSplitsASurrogatePair() {
    final var head = "s".repeat(TelegramTextFormat.MAX_TEXT_LENGTH - 1 - PREFIX.length());
    final var alert = IncidentAlert.build()
        .summary(head + "\uD83D\uDEA8 after the cut")
        .severity(IncidentSeverity.WARNING)
        .create();
    final var json = new TelegramTextFormat("chat-1").render(alert);
    final var text = renderedText(json);
    assertEquals(TelegramTextFormat.MAX_TEXT_LENGTH - 1, text.length());
    assertEquals(PREFIX + head, text);
    assertEquals("""
        {"chat_id":"chat-1","text":"%s%s"}""".formatted(PREFIX, head), json);
    assertStrictUtf8(json);
  }

  /// U+1F6A8 occupies indices 4,094 and 4,095, so its low half is the last unit kept: the
  /// cut is at the limit and the pair stays whole.
  @Test
  void aPairEndingAtTheLimitIsKept() {
    final var head = "s".repeat(TelegramTextFormat.MAX_TEXT_LENGTH - 2 - PREFIX.length());
    final var alert = IncidentAlert.build()
        .summary(head + "\uD83D\uDEA8 after the cut")
        .severity(IncidentSeverity.WARNING)
        .create();
    final var json = new TelegramTextFormat("chat-1").render(alert);
    final var text = renderedText(json);
    assertEquals(TelegramTextFormat.MAX_TEXT_LENGTH, text.length());
    assertEquals(PREFIX + head + "\uD83D\uDEA8", text);
    assertStrictUtf8(json);
  }

  /// Exactly at the limit is not over it: the text is sent as it is, even when its last
  /// unit is a high surrogate.
  @Test
  void textOfExactlyTheLimitIsSentAsItIsWhateverItsLastChar() {
    final var summary = "s".repeat(TelegramTextFormat.MAX_TEXT_LENGTH - 1 - PREFIX.length()) + '\uD83D';
    final var alert = IncidentAlert.build()
        .summary(summary)
        .severity(IncidentSeverity.WARNING)
        .create();
    assertEquals(TelegramTextFormat.MAX_TEXT_LENGTH, PREFIX.length() + summary.length());
    // compared as the rendered string: re-parsing would UTF-8 encode the lone half first
    assertEquals("""
            {"chat_id":"chat-1","text":"%s%s"}""".formatted(PREFIX, summary),
        new TelegramTextFormat("chat-1").render(alert)
    );
  }
}
