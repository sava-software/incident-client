package software.sava.incident.pagerduty.event.client;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.pagerduty.config.PagerDutyConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

/// `ServiceLoader` factory creating a [PagerDutyIncidentClient] from a [PagerDutyConfig];
/// registered under provider id `pagerduty`.
public final class PagerDutyIncidentClientFactory implements IncidentClientFactory {

  public PagerDutyIncidentClientFactory() {
  }

  @Override
  public String provider() {
    return "pagerduty";
  }

  @Override
  public IncidentClient createClient(final Properties properties, final String prefix) {
    return createClient(PagerDutyConfig.parseConfig(properties, prefix));
  }

  @Override
  public IncidentClient createClient(final JsonIterator ji) {
    return createClient(PagerDutyConfig.parseConfig(ji));
  }

  private static IncidentClient createClient(final PagerDutyConfig config) {
    return config.createClientBuilder().createClient().asIncidentClient();
  }
}
