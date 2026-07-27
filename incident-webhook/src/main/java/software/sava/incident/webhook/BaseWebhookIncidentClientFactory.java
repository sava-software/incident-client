package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.webhook.config.WebhookConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

/// Shared factory plumbing: every webhook provider id parses the same [WebhookConfig] and
/// differs only in the [WebhookFormat] paired with the client.
abstract class BaseWebhookIncidentClientFactory implements IncidentClientFactory {

  private final String provider;
  private final WebhookFormat format;

  BaseWebhookIncidentClientFactory(final String provider, final WebhookFormat format) {
    this.provider = provider;
    this.format = format;
  }

  @Override
  public final String provider() {
    return provider;
  }

  @Override
  public final IncidentClient createClient(final Properties properties, final String prefix) {
    return createClient(WebhookConfig.parseConfig(properties, prefix));
  }

  @Override
  public final IncidentClient createClient(final JsonIterator ji) {
    return createClient(WebhookConfig.parseConfig(ji));
  }

  private IncidentClient createClient(final WebhookConfig config) {
    return config.createClientBuilder().createClient().incidentClient(format);
  }
}
