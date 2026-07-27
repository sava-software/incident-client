package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;

import static software.sava.incident.core.json.JsonUtil.escapeJson;

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
/// No `parse_mode` is sent, so the text is plain, and no Telegram markup escaping
/// applies. Text over [#MAX_TEXT_LENGTH] characters is truncated client-side:
/// `sendMessage` rejects longer messages outright, and a truncated alert beats an
/// undelivered one.
public record TelegramTextFormat(String chatId) implements WebhookFormat {

  /// The `sendMessage` `text` limit, in characters.
  public static final int MAX_TEXT_LENGTH = 4_096;

  public TelegramTextFormat {
    if (chatId == null || chatId.isBlank()) {
      throw new IllegalStateException("TelegramTextFormat chatId is required.");
    }
  }

  @Override
  public String render(final IncidentAlert alert) {
    var text = WebhookFormats.renderPlainText(alert);
    if (text.length() > MAX_TEXT_LENGTH) {
      text = text.substring(0, MAX_TEXT_LENGTH);
    }
    return String.format("""
        {"chat_id":"%s","text":"%s"}""", escapeJson(chatId), escapeJson(text)
    );
  }
}
