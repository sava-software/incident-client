package software.sava.incident.webhook;

import software.sava.incident.webhook.config.WebhookConfig;

/// `ServiceLoader` factory creating a [WebhookIncidentClient] that POSTs
/// [TelegramTextFormat] messages to a Telegram Bot API `sendMessage` URL; registered
/// under provider id `telegram`. Configure the full URL as `endpoint` —
/// `https://api.telegram.org/bot<TOKEN>/sendMessage`, the URL carrying the credential —
/// and the destination as `chatId` (a numeric chat id or `@channelusername`).
public final class TelegramWebhookIncidentClientFactory extends BaseWebhookIncidentClientFactory {

  public TelegramWebhookIncidentClientFactory() {
    super("telegram");
  }

  @Override
  protected WebhookFormat format(final WebhookConfig config) {
    final var chatId = config.chatId();
    if (chatId == null || chatId.isBlank()) {
      throw new IllegalStateException("WebhookConfig chatId is required for the telegram provider.");
    }
    return new TelegramTextFormat(chatId);
  }
}
