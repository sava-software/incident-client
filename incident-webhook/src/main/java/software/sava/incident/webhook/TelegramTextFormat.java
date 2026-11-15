package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static software.sava.incident.core.json.JsonUtil.escapeJson;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// A [Telegram Bot API `sendMessage`](https://core.telegram.org/bots/api#sendmessage)
/// body, `{"chat_id":"...","text":"...","link_preview_options":{"is_disabled":true}}`,
/// rendering the alert as the same plain multi-line text as [WebhookFormats#SLACK_TEXT]
/// (minus Slack's entity escaping). The endpoint is the bot's `sendMessage` URL,
/// `https://api.telegram.org/bot<TOKEN>/sendMessage` — the URL carries the credential,
/// exactly like a Slack incoming webhook.
///
/// `chatId` targets the chat, group, or channel (a numeric id or `@channelusername`);
/// it is per-destination configuration, which is why this format carries state where the
/// [WebhookFormats] constants do not.
///
/// `messageThreadId` targets a topic of a forum supergroup: above zero it is sent as
/// `sendMessage`'s `message_thread_id`,
/// `{"chat_id":"...","message_thread_id":42,"text":"...",...}`; zero, the default and what
/// the one-argument constructor gives, sends none, so the message goes to the chat as a
/// whole (in a forum, its General topic). The Bot API reads the id as a 32-bit integer and treats
/// zero as no topic too; a negative id is refused here.
///
/// `silentSeverities` names the severities whose alerts are sent silently: for an alert of a
/// listed severity the body ends with `sendMessage`'s `disable_notification`,
/// `{...,"link_preview_options":{"is_disabled":true},"disable_notification":true}`, so chat
/// members get the message without a sound; for any other severity the field is not sent,
/// never sent as `false`, and the body is the one the two-argument format renders. Null or
/// empty, the default and what the one- and two-argument constructors give, silences none.
/// `CRITICAL` may be listed too, which silences pages in that chat. The set is copied, so a
/// later change to the caller's set changes nothing, and the accessor's set refuses mutation;
/// a null element is refused with a [NullPointerException].
///
/// Link previews are always disabled (`link_preview_options` with `is_disabled` `true`),
/// and there is no option to turn them back on: an alert is text, and a URL in it, such as
/// a dashboard or runbook link, should not grow a preview card or be fetched by Telegram's
/// preview crawler.
///
/// No `parse_mode` is sent, so the text is plain, and no Telegram markup escaping
/// applies. Text over [#MAX_TEXT_LENGTH] characters is truncated client-side, never
/// between the two halves of a surrogate pair (the cut then keeps one character fewer):
/// `sendMessage` rejects longer messages outright, and a truncated alert beats an
/// undelivered one.
///
/// A failed request's wait is read from the Bot API error body's `parameters.retry_after`
/// ([#retryAfter(String)], [#botApiRetryAfter(String)]).
///
/// A basic group that was upgraded to a supergroup refuses every later message with a 400
/// whose body names the supergroup, `parameters.migrate_to_chat_id`; [#movedTo(String)]
/// answers it with a format for that supergroup ([#botApiMigrateToChatId(String)]), and the
/// built-in incident adapter sends the alert there once more and logs a warning, on every
/// such report until `chatId` is updated. Only a basic-group `chatId` moves.
public record TelegramTextFormat(String chatId,
                                 int messageThreadId,
                                 Set<IncidentSeverity> silentSeverities) implements WebhookFormat {

  /// The `sendMessage` `text` limit, in characters.
  public static final int MAX_TEXT_LENGTH = 4_096;

  private static final Set<IncidentSeverity> NO_SILENT_SEVERITIES =
      Collections.unmodifiableSet(EnumSet.noneOf(IncidentSeverity.class));

  /// The lowest supergroup chat id: the Bot API's chat ids have at most 52 significant bits
  /// (`ResponseParameters.migrate_to_chat_id`).
  private static final long MIN_SUPERGROUP_CHAT_ID = -4_503_599_627_370_495L;
  /// The highest supergroup chat id: the Bot API server's chat id of supergroup 1,
  /// `-1000000000000 - supergroup_id`.
  private static final long MAX_SUPERGROUP_CHAT_ID = -1_000_000_000_001L;

  /// `chatId` is required; `messageThreadId` is zero for no topic and must not be negative;
  /// `silentSeverities` may be null or empty for none, and must not hold null.
  public TelegramTextFormat {
    if (chatId == null || chatId.isBlank()) {
      throw new IllegalStateException("TelegramTextFormat chatId is required.");
    }
    if (messageThreadId < 0) {
      throw new IllegalStateException("TelegramTextFormat messageThreadId must not be negative: " + messageThreadId + '.');
    }
    // EnumSet.copyOf refuses an empty collection that is not an EnumSet
    silentSeverities = silentSeverities == null || silentSeverities.isEmpty()
        ? NO_SILENT_SEVERITIES
        : Collections.unmodifiableSet(EnumSet.copyOf(silentSeverities));
  }

  /// A format for the chat as a whole: no topic (`messageThreadId` zero), and no severity
  /// sent silently.
  public TelegramTextFormat(final String chatId) {
    this(chatId, 0, null);
  }

  /// A format for a topic (`messageThreadId` above zero) or the chat as a whole (zero), with
  /// no severity sent silently.
  public TelegramTextFormat(final String chatId, final int messageThreadId) {
    this(chatId, messageThreadId, null);
  }

  @Override
  public String render(final IncidentAlert alert) {
    var text = WebhookFormats.renderPlainText(alert);
    if (text.length() > MAX_TEXT_LENGTH) {
      // a cut after a high surrogate would send its lone half, which UTF-8 cannot encode
      final int end = Character.isHighSurrogate(text.charAt(MAX_TEXT_LENGTH - 1))
          ? MAX_TEXT_LENGTH - 1
          : MAX_TEXT_LENGTH;
      text = text.substring(0, end);
    }
    final var topic = messageThreadId > 0
        ? ",\"message_thread_id\":" + Integer.toString(messageThreadId)
        : "";
    final var silent = silentSeverities.contains(alert.severity()) ? ",\"disable_notification\":true" : "";
    return String.format("""
        {"chat_id":"%s"%s,"text":"%s","link_preview_options":{"is_disabled":true}%s}""",
        escapeJson(chatId), topic, escapeJson(text), silent
    );
  }

  /// The Bot API error body's wait, as [#botApiRetryAfter(String)] reads it.
  @Override
  public Duration retryAfter(final String responseBody) {
    return botApiRetryAfter(responseBody);
  }

  /// The format for the supergroup a Bot API error body says this format's basic group was
  /// upgraded to, as [#botApiMigrateToChatId(String)] reads it; null when it names none, and
  /// always null unless `chatId` is a basic group's id: `-` followed by 1 to 12 ASCII digits,
  /// the first not `0`. A supergroup, channel, private chat or `@username` never moves,
  /// whatever the body says, and neither does a spelling of a basic group's id other than
  /// that one. The moved format keeps `silentSeverities` and sends no topic: a basic group
  /// cannot be a forum, so a configured `messageThreadId` never applied there. Its `chatId` is
  /// a supergroup's id, so it never moves again. Never throws.
  @Override
  public TelegramTextFormat movedTo(final String responseBody) {
    if (!isBasicGroupChatId(chatId)) {
      return null;
    }
    final long migrated = botApiMigrateToChatId(responseBody);
    return migrated == 0 ? null : new TelegramTextFormat(Long.toString(migrated), 0, silentSeverities);
  }

  /// Whether `chatId` is `-` followed by 1 to 12 ASCII digits, the first not `0`: the Bot API
  /// server's chat id of a basic group, `-basic_group_id`, from -1 to -999999999999.
  private static boolean isBasicGroupChatId(final String chatId) {
    final int length = chatId.length();
    if (length < 2 || length > 13 || chatId.charAt(0) != '-' || chatId.charAt(1) == '0') {
      return false;
    }
    for (int i = 1; i < length; ++i) {
      final char c = chatId.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  /// The chat id of the supergroup a
  /// [Telegram Bot API](https://core.telegram.org/bots/api) error body says a basic group was
  /// upgraded to, such as
  /// `{"ok":false,"error_code":400,"description":"Bad Request: group chat was upgraded to a supergroup chat","parameters":{"migrate_to_chat_id":-1001234567890}}`;
  /// otherwise 0, for a null or empty body too (no chat id is 0).
  ///
  /// The body must open with a JSON object, whitespace aside, and is read leniently as far as
  /// the end of that object, as [#botApiRetryAfter(String)] reads it: the id is read when the
  /// object's `ok` is the boolean `false` and its `parameters` is an object holding a
  /// `migrate_to_chat_id` number token that is `-` followed by ASCII digits, the first not
  /// `0`, that fits a `long` and lies from -4503599627370495 to -1000000000001: a supergroup's
  /// chat id, `-1000000000000 - supergroup_id`, within the 52 significant bits the Bot API
  /// promises. When a field name repeats, its last occurrence decides; an occurrence of
  /// another type resets the field, so a later `parameters` that is not an object, or a later
  /// `migrate_to_chat_id` that is not a number token, gives 0. The body need not be
  /// well-formed JSON to count: text after that object and the content of the fields it skips
  /// are not validated, and json-iterator takes a `null` token as the end of an object, in
  /// the objects read here too. A body that stops with the object still open is 0. Neither
  /// the HTTP status, the body's `error_code` nor its `description` is consulted, and the
  /// body is parsed on each call. Never throws.
  ///
  /// Public so a custom [WebhookFormat] that posts to the Bot API can answer its own
  /// [WebhookFormat#movedTo(String)] with it, and so a caller of
  /// [WebhookClient#post(String, WebhookFormat)] can follow an upgrade itself.
  public static long botApiMigrateToChatId(final String body) {
    return parseChatId(errorParameter(body, "migrate_to_chat_id"));
  }

  /// The wait a [Telegram Bot API](https://core.telegram.org/bots/api) error body states,
  /// such as
  /// `{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}`;
  /// otherwise null, for a null or empty body too.
  ///
  /// The body must open with a JSON object, whitespace aside, and is read leniently as far as
  /// the end of that object: the wait is read when the object's `ok` is the boolean `false`
  /// and its `parameters` is an object holding a
  /// `retry_after` number token of ASCII digits alone, without a leading zero, that fits a
  /// `long` and is above zero; that many seconds is the wait. When a field name repeats, its
  /// last occurrence decides. The body need not be well-formed JSON to count: text after that
  /// object and the content of the fields it skips are not validated, and json-iterator
  /// takes a `null` token as the end of an object, in the objects read here too. A body that
  /// stops with the object still open and nothing after it, such as
  /// `{"ok":false,"parameters":{"retry_after":35}`, is null. The Bot API documents the wait
  /// in the body (`ResponseParameters.retry_after`); its server also sends a `Retry-After`
  /// header, which the documented contract leaves out and which the built-in client prefers
  /// when it parses. Neither the HTTP status nor the body's `error_code` is consulted, and
  /// the body is parsed on each call. Never throws.
  ///
  /// Public so a custom [WebhookFormat] that posts to the Bot API can return it from its own
  /// [WebhookFormat#retryAfter(String)].
  public static Duration botApiRetryAfter(final String body) {
    final long seconds = parseSeconds(errorParameter(body, "retry_after"));
    return seconds > 0 ? Duration.ofSeconds(seconds) : null;
  }

  /// The number token of a Bot API error body's last `parameters.<field>`, as
  /// [JsonIterator#readNumberAsString()] returns it; empty, which is no number token and which
  /// both parsers read as none, when the body does not open with a JSON object, the object
  /// stops before its end, its last `ok` is not the boolean `false`, its last `parameters` is
  /// not an object, or that object's last `field` is not a number token. Each occurrence of
  /// `ok`, `parameters` or `field` replaces what an earlier one held. Never throws, and never
  /// returns null.
  private static String errorParameter(final String body, final String field) {
    try {
      final var ji = JsonIterator.parse(body);
      if (ji.whatIsNext() != ValueType.OBJECT) {
        return "";
      }
      final var error = new Object() {
        boolean notOk;
        String token = "";
      };
      ji.testObject((buf, offset, len, fieldJi) -> {
        if (fieldEquals("ok", buf, offset, len)) {
          if (fieldJi.whatIsNext() == ValueType.BOOLEAN) {
            error.notOk = !fieldJi.readBoolean();
          } else {
            error.notOk = false;
            fieldJi.skip();
          }
        } else if (fieldEquals("parameters", buf, offset, len)) {
          if (fieldJi.whatIsNext() == ValueType.OBJECT) {
            error.token = parameterToken(fieldJi, field);
          } else {
            error.token = "";
            fieldJi.skip();
          }
        } else {
          fieldJi.skip();
        }
        return true;
      });
      return error.notOk ? error.token : "";
    } catch (final RuntimeException malformed) {
      return "";
    }
  }

  /// The last `field` of the `parameters` object `ji` is at, as a number token; empty when it
  /// has none, or its last occurrence is not a number token.
  private static String parameterToken(final JsonIterator ji, final String field) {
    final var parameters = new Object() {
      String token = "";
    };
    ji.testObject((buf, offset, len, fieldJi) -> {
      if (fieldEquals(field, buf, offset, len)) {
        if (fieldJi.whatIsNext() == ValueType.NUMBER) {
          parameters.token = fieldJi.readNumberAsString();
        } else {
          parameters.token = "";
          fieldJi.skip();
        }
      } else {
        fieldJi.skip();
      }
      return true;
    });
    return parameters.token;
  }

  /// `number`, a number token as json-iterator read it, or empty for none, as a `long`; 0
  /// when it has a leading zero or [Long#parseLong(String)] refuses it, as it does the empty
  /// string, and negative for a sign, which the caller refuses. That this leaves ASCII digits
  /// alone rests on the tokenizer's alphabet: after [ValueType#NUMBER],
  /// [JsonIterator#readNumberAsString()] returns only characters from `0-9 . e E + -` and
  /// never starts with `+`, and of those `Long.parseLong` takes only digits and a leading `-`,
  /// so a fraction, an exponent or an inner sign fails the parse. The body is read as bytes,
  /// so no digit outside ASCII reaches the parse either.
  private static long parseSeconds(final String number) {
    if (number.startsWith("0")) {
      return 0;
    }
    try {
      return Long.parseLong(number);
    } catch (final NumberFormatException notALong) {
      return 0;
    }
  }

  /// `number`, a number token as json-iterator read it, or empty for none, as a supergroup's
  /// chat id; 0 when it is not `-` followed by ASCII digits, the first not `0`, that
  /// [Long#parseLong(String)] takes, or lies outside [#MIN_SUPERGROUP_CHAT_ID] to
  /// [#MAX_SUPERGROUP_CHAT_ID]. Only the leading zero needs its own check: by the tokenizer's
  /// alphabet, as [#parseSeconds(String)] argues, `Long.parseLong` refuses a fraction, an
  /// exponent, an inner sign and a lone `-`, and the range refuses a token without the sign.
  private static long parseChatId(final String number) {
    if (number.startsWith("-0")) {
      return 0;
    }
    final long chatId;
    try {
      chatId = Long.parseLong(number);
    } catch (final NumberFormatException notALong) {
      return 0;
    }
    return chatId >= MIN_SUPERGROUP_CHAT_ID && chatId <= MAX_SUPERGROUP_CHAT_ID ? chatId : 0;
  }
}
