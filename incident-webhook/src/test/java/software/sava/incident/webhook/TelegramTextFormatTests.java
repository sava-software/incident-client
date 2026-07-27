package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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
}
