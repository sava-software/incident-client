package software.sava.incident.io.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClients;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.io.IncidentIoIncidentClient;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the `incident.io-alerts` provider through [IncidentClients], in both config forms,
/// including what a client built from config sends.
final class IncidentIoAlertIncidentClientFactoryTests {

  private HttpServer server;

  private record Request(String path, Map<String, List<String>> headers, String body) {
  }

  private String serve(final List<Request> requests) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        requests.add(new Request(
            exchange.getRequestURI().getRawPath(),
            Map.copyOf(exchange.getRequestHeaders()),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
        ));
        final var bytes = """
            {"status":"success","message":"Event accepted for processing","deduplication_key":"wire-key"}"""
            .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(202, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return "http://" + address.getHostString() + ':' + address.getPort() + "/custom/alert-source";
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  @Test
  void theProviderId() {
    assertEquals("incident.io-alerts", new IncidentIoAlertIncidentClientFactory().provider());
  }

  @Test
  void createFromProperties() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "incident.io-alerts");
    properties.setProperty("incident.token", "source-secret");
    properties.setProperty("incident.alertSourceConfigId", "01GW2G3V0S59R238FAHPDS1R66");
    final var client = IncidentClients.createClient(properties, "incident");
    assertInstanceOf(IncidentIoAlertIncidentClient.class, client);
    assertEquals(URI.create("https://api.incident.io/v2/alert_events/http/01GW2G3V0S59R238FAHPDS1R66"),
        client.endpoint());
    assertTrue(client.supportsResolve());
  }

  @Test
  void providerIdVariantsMatch() {
    for (final var provider : new String[]{"incident.io-alerts", "IncidentIoAlerts", "incident-io-alerts", "INCIDENT_IO_ALERTS"}) {
      final var properties = new Properties();
      properties.setProperty("provider", provider);
      properties.setProperty("token", "t");
      properties.setProperty("alertSourceConfigId", "src");
      assertInstanceOf(IncidentIoAlertIncidentClient.class, IncidentClients.createClient(properties), provider);
    }
    // the Create Incident provider is still its own
    final var properties = new Properties();
    properties.setProperty("provider", "incident.io");
    properties.setProperty("bearerToken", "t");
    properties.setProperty("visibility", "public");
    assertInstanceOf(IncidentIoIncidentClient.class, IncidentClients.createClient(properties));
  }

  @Test
  void createFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"IncidentIoAlerts","config":{"token":"t","alertSourceConfigId":"src 1"}}"""));
    assertInstanceOf(IncidentIoAlertIncidentClient.class, client);
    assertEquals("/v2/alert_events/http/src%201", client.endpoint().getRawPath());
  }

  @Test
  void aClientFromPropertiesSendsWhatItsConfigSays() throws IOException {
    final var requests = new CopyOnWriteArrayList<Request>();
    final var endpoint = serve(requests);
    final var properties = new Properties();
    properties.setProperty("incident.provider", "incident.io-alerts");
    properties.setProperty("incident.token", "source-secret");
    properties.setProperty("incident.endpoint", endpoint);
    properties.setProperty("incident.resolveTitle", "Recovered");
    final var client = IncidentClients.createClient(properties, "incident");

    final var reported = client.reportIncident(IncidentAlert.build()
        .key("alert-key")
        .summary("Disk full")
        .severity(IncidentSeverity.ERROR)
        .create()).join();
    final var resolved = client.resolveIncident("alert-key").join();

    assertEquals("wire-key", reported.key());
    assertEquals("success", resolved.status());
    assertEquals(2, requests.size());
    for (final var request : requests) {
      assertEquals("/custom/alert-source", request.path());
      assertEquals(List.of("Bearer source-secret"), request.headers().get("Authorization"));
    }
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"alert-key","metadata":{"severity":"error"}}""",
        requests.getFirst().body());
    assertEquals("""
        {"title":"Recovered","status":"resolved","deduplication_key":"alert-key"}""", requests.getLast().body());
  }

  @Test
  void aClientFromJsonSendsWhatItsConfigSays() throws IOException {
    final var requests = new CopyOnWriteArrayList<Request>();
    final var endpoint = serve(requests);
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"incident.io-alerts","config":{"token":"json-secret","endpoint":"%s","resolveTitle":"Back to normal"}}"""
        .formatted(endpoint)));

    client.resolveIncident("k-1").join();

    assertEquals(1, requests.size());
    assertEquals(List.of("Bearer json-secret"), requests.getFirst().headers().get("Authorization"));
    assertEquals("""
        {"title":"Back to normal","status":"resolved","deduplication_key":"k-1"}""", requests.getFirst().body());
  }
}
