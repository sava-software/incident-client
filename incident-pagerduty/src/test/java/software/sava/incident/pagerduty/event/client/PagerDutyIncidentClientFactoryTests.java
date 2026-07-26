package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentClients;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyIncidentClientFactoryTests {

  @Test
  void asIncidentClient() {
    final var eventClient = PagerDutyEventClient.clientBuilder()
        .defaultRoutingKey("rk-1")
        .createClient();
    final var client = eventClient.asIncidentClient();
    assertInstanceOf(PagerDutyIncidentClient.class, client);
    assertTrue(client.supportsResolve());
    assertSame(eventClient.endpoint(), client.endpoint());
    assertSame(eventClient.httpClient(), client.httpClient());
  }

  @Test
  void createFromProperties() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "pagerduty");
    properties.setProperty("incident.routingKey", "rk-1");
    final var client = IncidentClients.createClient(properties, "incident");
    assertInstanceOf(PagerDutyIncidentClient.class, client);
    assertEquals(URI.create("https://events.pagerduty.com"), client.endpoint());
  }

  @Test
  void providerMatchIgnoresCaseAndSeparators() {
    final var properties = new Properties();
    properties.setProperty("provider", "Pager-Duty");
    properties.setProperty("routingKey", "rk-1");
    assertInstanceOf(PagerDutyIncidentClient.class, IncidentClients.createClient(properties));
  }

  @Test
  void createFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"pagerduty","config":{"routingKey":"rk-1","endpoint":"https://events.eu.pagerduty.com"}}"""));
    assertInstanceOf(PagerDutyIncidentClient.class, client);
    assertEquals(URI.create("https://events.eu.pagerduty.com"), client.endpoint());
  }

  @Test
  void configValidationSurfacesThroughFactory() {
    final var properties = new Properties();
    properties.setProperty("provider", "pagerduty");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void unknownProviderListsAvailable() {
    final var properties = new Properties();
    properties.setProperty("provider", "nonesuch");
    properties.setProperty("routingKey", "rk-1");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    assertTrue(ex.getMessage().contains("pagerduty"));
  }
}
