package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import java.time.Duration;

import static software.sava.incident.core.json.JsonUtil.escapeJson;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// A [Telegram Bot API `sendMessage`](https://core.telegram.org/bots/api#sendmessage)
/// body, `{"chat_id":"...","text":"..."}`, rendering the alert as the same plain
/// multi-line text as [WebhookFormats#SLACK_TEXT] (minus Slack's entity escaping). The
/// endpoint is the bot's `sendMessage` URL,
/// `https://api.telegram.org/bot<TOKEN>/sendMessage` — the URL carries the credential,
/// exactly like a Slack incoming webhook.
///
/// `chatId` targets the chat, group, or channel (a numeric id or `@channelusername`);
/// it is per-destination configuration, which is why this format carries state where the
/// [WebhookFormats] constants do not.
///
/// `messageThreadId` targets a topic of a forum supergroup: above zero it is sent as
/// `sendMessage`'s `message_thread_id`,
/// `{"chat_id":"...","message_thread_id":42,"text":"..."}`; zero, the default and what the
/// one-argument constructor gives, sends none, so the message goes to the chat as a whole
/// (in a forum, its General topic). The Bot API reads the id as a 32-bit integer and treats
/// zero as no topic too; a negative id is refused here.
///
/// No `parse_mode` is sent, so the text is plain, and no Telegram markup escaping
/// applies. Text over [#MAX_TEXT_LENGTH] characters is truncated client-side, never
/// between the two halves of a surrogate pair (the cut then keeps one character fewer):
/// `sendMessage` rejects longer messages outright, and a truncated alert beats an
/// undelivered one.
///
/// A failed request's wait is read from the Bot API error body's `parameters.retry_after`
/// ([#retryAfter(String)], [#botApiRetryAfter(String)]).
public record TelegramTextFormat(String chatId, int messageThreadId) implements WebhookFormat {

  /// The `sendMessage` `text` limit, in characters.
  public static final int MAX_TEXT_LENGTH = 4_096;

  /// `chatId` is required; `messageThreadId` is zero for no topic and must not be negative.
  public TelegramTextFormat {
    if (chatId == null || chatId.isBlank()) {
      throw new IllegalStateException("TelegramTextFormat chatId is required.");
    }
    if (messageThreadId < 0) {
      throw new IllegalStateException("TelegramTextFormat messageThreadId must not be negative: " + messageThreadId + '.');
    }
  }

  /// A format for the chat as a whole: no topic (`messageThreadId` zero).
  public TelegramTextFormat(final String chatId) {
    this(chatId, 0);
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
    return String.format("""
        {"chat_id":"%s"%s,"text":"%s"}""", escapeJson(chatId), topic, escapeJson(text)
    );
  }

  /// The Bot API error body's wait, as [#botApiRetryAfter(String)] reads it.
  @Override
  public Duration retryAfter(final String responseBody) {
    return botApiRetryAfter(responseBody);
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
    try {
      final var ji = JsonIterator.parse(body);
      if (ji.whatIsNext() != ValueType.OBJECT) {
        return null;
      }
      final var error = new Object() {
        boolean notOk;
        long retryAfter;
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
            error.retryAfter = parametersRetryAfter(fieldJi);
          } else {
            error.retryAfter = 0;
            fieldJi.skip();
          }
        } else {
          fieldJi.skip();
        }
        return true;
      });
      return error.notOk && error.retryAfter > 0 ? Duration.ofSeconds(error.retryAfter) : null;
    } catch (final RuntimeException malformed) {
      return null;
    }
  }

  /// The `retry_after` of a `parameters` object as [#parseSeconds(String)] reads it, or 0 when
  /// it has none that is a number token; the caller takes only a value above zero.
  private static long parametersRetryAfter(final JsonIterator ji) {
    final var parameters = new Object() {
      long retryAfter;
    };
    ji.testObject((buf, offset, len, fieldJi) -> {
      if (fieldEquals("retry_after", buf, offset, len)) {
        if (fieldJi.whatIsNext() == ValueType.NUMBER) {
          parameters.retryAfter = parseSeconds(fieldJi.readNumberAsString());
        } else {
          parameters.retryAfter = 0;
          fieldJi.skip();
        }
      } else {
        fieldJi.skip();
      }
      return true;
    });
    return parameters.retryAfter;
  }

  /// `number`, a number token as json-iterator read it, as a `long`; 0 when it has a leading
  /// zero or [Long#parseLong(String)] refuses it, and negative for a sign, which the caller
  /// refuses. That this leaves ASCII digits alone rests on the tokenizer's alphabet: after
  /// [ValueType#NUMBER], [JsonIterator#readNumberAsString()] returns only characters from
  /// `0-9 . e E + -` and never starts with `+`, and of those `Long.parseLong` takes only
  /// digits and a leading `-`, so a fraction, an exponent or an inner sign fails the parse.
  /// The body is read as bytes, so no digit outside ASCII reaches the parse either.
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
}
