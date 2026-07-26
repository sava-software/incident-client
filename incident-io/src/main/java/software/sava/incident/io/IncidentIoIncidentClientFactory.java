package software.sava.incident.io;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.io.config.IncidentIoConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

/// `ServiceLoader` factory creating an [IncidentIoIncidentClient] from an
/// [IncidentIoConfig], including its severity id mapping and default
/// type/status/visibility/mode; registered under provider id `incident.io`.
public final class IncidentIoIncidentClientFactory implements IncidentClientFactory {

  public IncidentIoIncidentClientFactory() {
  }

  @Override
  public String provider() {
    return "incident.io";
  }

  @Override
  public IncidentClient createClient(final Properties properties, final String prefix) {
    return createClient(IncidentIoConfig.parseConfig(properties, prefix));
  }

  @Override
  public IncidentClient createClient(final JsonIterator ji) {
    return createClient(IncidentIoConfig.parseConfig(ji));
  }

  private static IncidentClient createClient(final IncidentIoConfig config) {
    final var client = config.createClientBuilder().createClient();
    return config.createIncidentClientBuilder(client).createClient();
  }
}
