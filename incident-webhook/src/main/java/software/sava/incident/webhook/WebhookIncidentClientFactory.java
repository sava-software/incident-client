package software.sava.incident.webhook;

import software.sava.incident.webhook.config.WebhookConfig;

/// `ServiceLoader` factory creating a [WebhookIncidentClient] that POSTs the canonical
/// [WebhookFormats#GENERIC_JSON] alert document; registered under provider id `webhook`.
public final class WebhookIncidentClientFactory extends BaseWebhookIncidentClientFactory {

  public WebhookIncidentClientFactory() {
    super("webhook");
  }

  @Override
  WebhookFormat format(final WebhookConfig config) {
    return WebhookFormats.GENERIC_JSON;
  }
}
