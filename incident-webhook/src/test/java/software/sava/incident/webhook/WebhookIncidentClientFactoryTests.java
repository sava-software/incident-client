package software.sava.incident.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClients;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

final class WebhookIncidentClientFactoryTests {

  @Test
  void createFromPropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "webhook");
    properties.setProperty("incident.endpoint", "https://hooks.example.com/notify");
    final var client = IncidentClients.createClient(properties, "incident");
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertSame(WebhookFormats.GENERIC_JSON, webhookClient.format());
    assertEquals(URI.create("https://hooks.example.com/notify"), client.endpoint());
    assertFalse(client.supportsResolve());
  }

  @Test
  void providerMatchIgnoresCaseAndSeparators() {
    final var properties = new Properties();
    properties.setProperty("provider", "Slack");
    properties.setProperty("endpoint", "https://hooks.slack.com/services/T000/B000/secret");
    final var client = IncidentClients.createClient(properties);
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertSame(WebhookFormats.SLACK_TEXT, webhookClient.format());
  }

  @Test
  void createFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"slack","config":{"endpoint":"https://hooks.slack.com/services/T000/B000/secret"}}"""));
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertSame(WebhookFormats.SLACK_TEXT, webhookClient.format());
    assertEquals(URI.create("https://hooks.slack.com/services/T000/B000/secret"), client.endpoint());
  }

  @Test
  void missingEndpointFails() {
    final var properties = new Properties();
    properties.setProperty("provider", "webhook");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    assertEquals("WebhookConfig endpoint is required.", ex.getMessage());
  }

  /// End-to-end through configuration: the config's headers and bearer token must reach
  /// the wire, and the slack provider must POST a rendered text message.
  @Test
  void configuredClientPostsToTheWire() throws IOException {
    final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    final var requestFuture = new CompletableFuture<Map.Entry<Map<String, List<String>>, String>>();
    server.createContext("/", (final HttpExchange exchange) -> {
      try (exchange) {
        final var body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
        requestFuture.complete(Map.entry(exchange.getRequestHeaders(), body));
        final var ok = "ok".getBytes(UTF_8);
        exchange.sendResponseHeaders(200, ok.length);
        exchange.getResponseBody().write(ok);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    try {
      final var endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + "/hook";
      final var client = IncidentClients.createClient(JsonIterator.parse(String.format("""
          {"provider":"slack","config":{"endpoint":"%s","bearerToken":"token-1","headers":{"X-From-Config":"1"}}}""", endpoint)));

      final var response = client.reportIncident(IncidentAlert.build()
          .summary("summary-1")
          .severity(IncidentSeverity.CRITICAL)
          .create()).join();

      assertEquals("ok", response.status());
      final var request = requestFuture.join();
      assertEquals(List.of("1"), request.getKey().get("X-from-config"));
      assertEquals(List.of("Bearer token-1"), request.getKey().get("Authorization"));
      assertEquals("""
          {"text":"[CRITICAL] summary-1"}""", request.getValue());
    } finally {
      server.stop(0);
    }
  }
}
