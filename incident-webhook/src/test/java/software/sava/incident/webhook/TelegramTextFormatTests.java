package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.incident.core.api.IncidentSeverity.*;
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
            {"chat_id":"-1001234567890","text":"[CRITICAL] db \\"primary\\" down\\nline one\\nline two\\nSource: validator-01.example.com\\nTime: 2026-07-26T12:30:45Z\\nKey: dedup-1\\nslot: 350000000","link_preview_options":{"is_disabled":true}}""",
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
            {"chat_id":"@channel","text":"[ERROR] a & b <tag> *bold* _it_","link_preview_options":{"is_disabled":true}}""",
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
            {"chat_id":"a\\"b","text":"[INFO] summary-1","link_preview_options":{"is_disabled":true}}""",
        new TelegramTextFormat("a\"b").render(alert)
    );
  }

  @Test
  void chatIdIsRequired() {
    final var nullEx = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(null));
    assertEquals("TelegramTextFormat chatId is required.", nullEx.getMessage());
    assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(" "));
  }

  private static final IncidentAlert TOPIC_ALERT = IncidentAlert.build()
      .summary("summary-1")
      .severity(IncidentSeverity.INFO)
      .create();

  @Test
  void aTopicIsSentAsMessageThreadId() {
    assertEquals("""
            {"chat_id":"-100123","message_thread_id":1,"text":"[INFO] summary-1","link_preview_options":{"is_disabled":true}}""",
        new TelegramTextFormat("-100123", 1).render(TOPIC_ALERT)
    );
    assertEquals("""
            {"chat_id":"-100123","message_thread_id":40961,"text":"[INFO] summary-1","link_preview_options":{"is_disabled":true}}""",
        new TelegramTextFormat("-100123", 40_961).render(TOPIC_ALERT)
    );
    assertEquals("""
            {"chat_id":"-100123","message_thread_id":2147483647,"text":"[INFO] summary-1","link_preview_options":{"is_disabled":true}}""",
        new TelegramTextFormat("-100123", Integer.MAX_VALUE).render(TOPIC_ALERT)
    );
  }

  /// Zero is no topic: the body is the one the one-argument format renders, byte for byte.
  @Test
  void topicZeroSendsNoMessageThreadId() {
    final var expected = """
        {"chat_id":"-100123","text":"[INFO] summary-1","link_preview_options":{"is_disabled":true}}""";
    assertEquals(expected, new TelegramTextFormat("-100123", 0).render(TOPIC_ALERT));
    assertEquals(expected, new TelegramTextFormat("-100123").render(TOPIC_ALERT));
  }

  @Test
  void theOneArgumentConstructorMeansNoTopic() {
    final var format = new TelegramTextFormat("chat-1");
    assertEquals(new TelegramTextFormat("chat-1", 0), format);
    assertEquals(new TelegramTextFormat("chat-1", 0, null), format);
    assertEquals("chat-1", format.chatId());
    assertEquals(0, format.messageThreadId());
    assertEquals(Set.of(), format.silentSeverities());
    final var topic = new TelegramTextFormat("chat-1", 7);
    assertEquals(7, topic.messageThreadId());
    assertEquals("chat-1", topic.chatId());
    assertEquals(Set.of(), topic.silentSeverities());
    assertEquals(new TelegramTextFormat("chat-1", 7, null), topic);
  }

  @Test
  void aNegativeTopicIsRefused() {
    final var minusOne = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat("chat-1", -1));
    assertEquals("TelegramTextFormat messageThreadId must not be negative: -1.", minusOne.getMessage());
    final var min = assertThrows(IllegalStateException.class,
        () -> new TelegramTextFormat("chat-1", Integer.MIN_VALUE));
    assertEquals("TelegramTextFormat messageThreadId must not be negative: -2147483648.", min.getMessage());
  }

  /// The chat id is checked before the topic.
  @Test
  void aMissingChatIdIsReportedBeforeANegativeTopic() {
    final var ex = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(null, -1));
    assertEquals("TelegramTextFormat chatId is required.", ex.getMessage());
  }

  @Test
  void toStringNamesEveryComponent() {
    assertEquals("TelegramTextFormat[chatId=chat-1, messageThreadId=0, silentSeverities=[]]",
        new TelegramTextFormat("chat-1").toString());
    assertEquals("TelegramTextFormat[chatId=@channel, messageThreadId=42, silentSeverities=[]]",
        new TelegramTextFormat("@channel", 42).toString());
    // declaration order, whatever the order given
    assertEquals("TelegramTextFormat[chatId=chat-1, messageThreadId=7, silentSeverities=[ERROR, INFO]]",
        new TelegramTextFormat("chat-1", 7, new LinkedHashSet<>(List.of(INFO, ERROR))).toString());
  }

  private static IncidentAlert summaryAlert(final IncidentSeverity severity) {
    return IncidentAlert.build()
        .summary("summary-1")
        .severity(severity)
        .create();
  }

  @Test
  void aSilentSeverityAddsDisableNotification() {
    assertEquals("""
            {"chat_id":"-100123","text":"[INFO] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        new TelegramTextFormat("-100123", 0, EnumSet.of(INFO)).render(TOPIC_ALERT)
    );
    assertEquals("""
            {"chat_id":"-100123","message_thread_id":42,"text":"[INFO] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        new TelegramTextFormat("-100123", 42, EnumSet.of(INFO)).render(TOPIC_ALERT)
    );
  }

  /// Only a listed severity is silent; any other renders the body without the field.
  @Test
  void silenceIsPerSeverity() {
    final var format = new TelegramTextFormat("-100123", 0, EnumSet.of(INFO, WARNING));
    assertEquals("""
            {"chat_id":"-100123","text":"[INFO] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        format.render(summaryAlert(INFO))
    );
    assertEquals("""
            {"chat_id":"-100123","text":"[WARNING] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        format.render(summaryAlert(WARNING))
    );
    assertEquals("""
            {"chat_id":"-100123","text":"[ERROR] summary-1","link_preview_options":{"is_disabled":true}}""",
        format.render(summaryAlert(ERROR))
    );
    assertEquals("""
            {"chat_id":"-100123","text":"[CRITICAL] summary-1","link_preview_options":{"is_disabled":true}}""",
        format.render(summaryAlert(CRITICAL))
    );
  }

  /// CRITICAL may be listed, alone or with every other severity.
  @Test
  void everySeverityCanBeSilent() {
    final var all = new TelegramTextFormat("-100123", 0, EnumSet.allOf(IncidentSeverity.class));
    for (final var severity : IncidentSeverity.values()) {
      assertEquals("""
              {"chat_id":"-100123","text":"[%s] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}"""
              .formatted(severity.name()),
          all.render(summaryAlert(severity)), severity.name()
      );
    }
    assertEquals("""
            {"chat_id":"-100123","text":"[CRITICAL] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        new TelegramTextFormat("-100123", 0, Set.of(CRITICAL)).render(summaryAlert(CRITICAL))
    );
  }

  /// Null and every empty set mean none: the body is today's, byte for byte, for every
  /// severity, and the formats are equal.
  @Test
  void noSilentSeveritiesIsTodaysBody() {
    final var expected = new TelegramTextFormat("-100123", 0);
    final var formats = List.of(
        new TelegramTextFormat("-100123", 0, null),
        new TelegramTextFormat("-100123", 0, Set.of()),
        new TelegramTextFormat("-100123", 0, EnumSet.noneOf(IncidentSeverity.class)),
        new TelegramTextFormat("-100123"),
        expected
    );
    for (final var format : formats) {
      assertEquals(expected, format);
      assertEquals(expected.hashCode(), format.hashCode());
      assertEquals(Set.of(), format.silentSeverities());
      for (final var severity : IncidentSeverity.values()) {
        assertEquals("""
                {"chat_id":"-100123","text":"[%s] summary-1","link_preview_options":{"is_disabled":true}}"""
                .formatted(severity.name()),
            format.render(summaryAlert(severity)), severity.name()
        );
      }
    }
  }

  @Test
  void silentSeveritiesAreCopiedAndImmutable() {
    final var source = EnumSet.of(INFO);
    final var format = new TelegramTextFormat("-100123", 0, source);
    source.add(ERROR);
    source.remove(INFO);
    assertEquals(Set.of(INFO), format.silentSeverities());
    assertEquals(new TelegramTextFormat("-100123", 0, EnumSet.of(INFO)), format);
    assertEquals("""
            {"chat_id":"-100123","text":"[INFO] summary-1","link_preview_options":{"is_disabled":true},"disable_notification":true}""",
        format.render(summaryAlert(INFO))
    );
    assertEquals("""
            {"chat_id":"-100123","text":"[ERROR] summary-1","link_preview_options":{"is_disabled":true}}""",
        format.render(summaryAlert(ERROR))
    );
    assertThrows(UnsupportedOperationException.class, () -> format.silentSeverities().add(ERROR));
    assertThrows(UnsupportedOperationException.class, () -> format.silentSeverities().remove(INFO));

    // the empty default is just as unmodifiable, whichever way it was given
    final var emptySource = EnumSet.noneOf(IncidentSeverity.class);
    final var empty = new TelegramTextFormat("-100123", 0, emptySource);
    emptySource.add(INFO);
    assertEquals(Set.of(), empty.silentSeverities());
    assertThrows(UnsupportedOperationException.class, () -> empty.silentSeverities().add(INFO));
    assertThrows(UnsupportedOperationException.class, () -> new TelegramTextFormat("-100123").silentSeverities().add(INFO));
  }

  @Test
  void silentSeveritiesEqualityIgnoresOrderAndImplementation() {
    final var fromList = new TelegramTextFormat("-100123", 0, new LinkedHashSet<>(List.of(WARNING, INFO)));
    final var fromEnumSet = new TelegramTextFormat("-100123", 0, EnumSet.of(INFO, WARNING));
    assertEquals(fromEnumSet, fromList);
    assertEquals(fromEnumSet.hashCode(), fromList.hashCode());
    assertEquals(Set.of(INFO, WARNING), fromList.silentSeverities());
    assertNotEquals(new TelegramTextFormat("-100123", 0, EnumSet.of(INFO)), fromEnumSet);
    assertNotEquals(new TelegramTextFormat("-100123", 0), fromEnumSet);
    // never a throw for a null probe, which Set.of would give
    assertFalse(fromEnumSet.silentSeverities().contains(null));
    assertFalse(new TelegramTextFormat("-100123").silentSeverities().contains(null));
  }

  @Test
  void aNullSeverityElementIsRefused() {
    final var withNull = new LinkedHashSet<IncidentSeverity>();
    withNull.add(INFO);
    withNull.add(null);
    assertThrows(NullPointerException.class, () -> new TelegramTextFormat("-100123", 0, withNull));
  }

  /// The chat id and the topic are checked before the set.
  @Test
  void theChatIdAndTopicAreCheckedBeforeTheSilentSeverities() {
    final var withNull = new LinkedHashSet<IncidentSeverity>();
    withNull.add(null);
    final var chatId = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat(null, 0, withNull));
    assertEquals("TelegramTextFormat chatId is required.", chatId.getMessage());
    final var topic = assertThrows(IllegalStateException.class, () -> new TelegramTextFormat("chat-1", -1, withNull));
    assertEquals("TelegramTextFormat messageThreadId must not be negative: -1.", topic.getMessage());
  }

  @Test
  void textTruncatesAtTheSendMessageLimit() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .details("d".repeat(TelegramTextFormat.MAX_TEXT_LENGTH + 100))
        .severity(IncidentSeverity.WARNING)
        .create();
    assertTruncatedBody(new TelegramTextFormat("chat-1", 9).render(alert), false);
    assertTruncatedBody(new TelegramTextFormat("chat-1", 9, EnumSet.of(WARNING)).render(alert), true);
  }

  private static void assertTruncatedBody(final String json, final boolean silent) {
    final var parsed = new Object() {
      String chatId, text;
      int messageThreadId;
      boolean previewsDisabled;
      Boolean disableNotification;
    };
    JsonIterator.parse(json).testObject((buf, offset, len, ji) -> {
      if (fieldEquals("chat_id", buf, offset, len)) {
        parsed.chatId = ji.readString();
      } else if (fieldEquals("message_thread_id", buf, offset, len)) {
        assertEquals(ValueType.NUMBER, ji.whatIsNext());
        parsed.messageThreadId = Integer.parseInt(ji.readNumberAsString());
      } else if (fieldEquals("text", buf, offset, len)) {
        parsed.text = ji.readString();
      } else if (fieldEquals("disable_notification", buf, offset, len)) {
        assertEquals(ValueType.BOOLEAN, ji.whatIsNext());
        parsed.disableNotification = ji.readBoolean();
      } else if (fieldEquals("link_preview_options", buf, offset, len)) {
        assertEquals(ValueType.OBJECT, ji.whatIsNext());
        ji.testObject((buf2, offset2, len2, ji2) -> {
          if (fieldEquals("is_disabled", buf2, offset2, len2)) {
            assertEquals(ValueType.BOOLEAN, ji2.whatIsNext());
            parsed.previewsDisabled = ji2.readBoolean();
          } else {
            throw new AssertionError("unexpected link_preview_options field " + new String(buf2, offset2, len2));
          }
          return true;
        });
      } else {
        throw new AssertionError("unexpected field " + new String(buf, offset, len));
      }
      return true;
    });
    assertEquals("chat-1", parsed.chatId);
    assertEquals(9, parsed.messageThreadId);
    assertTrue(parsed.previewsDisabled);
    // present as the boolean true when silent, absent otherwise
    assertEquals(silent ? Boolean.TRUE : null, parsed.disableNotification);
    assertEquals(TelegramTextFormat.MAX_TEXT_LENGTH, parsed.text.length());
    assertTrue(parsed.text.startsWith("[WARNING] summary-1\nd"));
    assertTrue(json.endsWith(silent
        ? "\"link_preview_options\":{\"is_disabled\":true},\"disable_notification\":true}"
        : "\"link_preview_options\":{\"is_disabled\":true}}"));
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
        {"chat_id":"chat-1","text":"%s%s","link_preview_options":{"is_disabled":true}}""".formatted(prefix, summary), json);
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
        {"chat_id":"chat-1","text":"%s%s","link_preview_options":{"is_disabled":true}}""".formatted(PREFIX, head), json);
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
            {"chat_id":"chat-1","text":"%s%s","link_preview_options":{"is_disabled":true}}""".formatted(PREFIX, summary),
        new TelegramTextFormat("chat-1").render(alert)
    );
  }
}
