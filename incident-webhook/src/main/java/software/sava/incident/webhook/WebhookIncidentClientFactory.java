package software.sava.incident.webhook;

/// `ServiceLoader` factory creating a [WebhookIncidentClient] that POSTs the canonical
/// [WebhookFormats#GENERIC_JSON] alert document; registered under provider id `webhook`.
public final class WebhookIncidentClientFactory extends BaseWebhookIncidentClientFactory {

  public WebhookIncidentClientFactory() {
    super("webhook", WebhookFormats.GENERIC_JSON);
  }
}
