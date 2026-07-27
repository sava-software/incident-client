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

  BaseWebhookIncidentClientFactory(final String provider) {
    this.provider = provider;
  }

  /// The [WebhookFormat] paired with the created client. Stateless providers ignore
  /// `config`; config-dependent formats (Telegram's chat id) read their state from it and
  /// throw an IllegalStateException when it is missing.
  abstract WebhookFormat format(final WebhookConfig config);

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
    return config.createClientBuilder().createClient().incidentClient(format(config));
  }
}
