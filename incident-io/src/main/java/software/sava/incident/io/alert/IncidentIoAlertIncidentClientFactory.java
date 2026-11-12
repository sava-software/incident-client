package software.sava.incident.io.alert;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.io.config.IncidentIoAlertSourceConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

/// `ServiceLoader` factory creating an [IncidentIoAlertIncidentClient] from an
/// [IncidentIoAlertSourceConfig]; registered under provider id `incident.io-alerts`.
public final class IncidentIoAlertIncidentClientFactory implements IncidentClientFactory {

  public IncidentIoAlertIncidentClientFactory() {
  }

  @Override
  public String provider() {
    return "incident.io-alerts";
  }

  @Override
  public IncidentClient createClient(final Properties properties, final String prefix) {
    return createClient(IncidentIoAlertSourceConfig.parseConfig(properties, prefix));
  }

  @Override
  public IncidentClient createClient(final JsonIterator ji) {
    return createClient(IncidentIoAlertSourceConfig.parseConfig(ji));
  }

  private static IncidentClient createClient(final IncidentIoAlertSourceConfig config) {
    final var client = config.createClientBuilder().createClient();
    return config.createIncidentClientBuilder(client).createClient();
  }
}
