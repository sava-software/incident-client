package software.sava.incident.webhook.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.sava.incident.webhook.WebhookClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.*;

/// Pins that parsed configuration actually reaches the created client: headers and the
/// bearer token are only observable on the wire, not through builder accessors.
final class WebhookConfigWiringTests {

  private record WireCapture(HttpServer server, String endpoint,
                             CompletableFuture<Map<String, List<String>>> requestHeaders) {
  }

  private static WireCapture startServer() throws IOException {
    final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    final var requestHeaders = new CompletableFuture<Map<String, List<String>>>();
    server.createContext("/", exchange -> {
      try (exchange) {
        exchange.getRequestBody().readAllBytes();
        requestHeaders.complete(Map.copyOf(exchange.getRequestHeaders()));
        exchange.sendResponseHeaders(200, -1);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + "/hook";
    return new WireCapture(server, endpoint, requestHeaders);
  }

  @Test
  void configuredHeadersAndBearerTokenReachTheWire() throws IOException {
    final var wire = startServer();
    try {
      final var properties = new Properties();
      properties.setProperty("endpoint", wire.endpoint());
      properties.setProperty("bearerToken", "token-1");
      properties.setProperty("headers.X-Api-Key", "key-1");
      final var client = WebhookConfig.parseConfig(properties)
          .createClientBuilder()
          .createClient();
      client.post("{}").join();

      final var headers = wire.requestHeaders().join();
      assertEquals(List.of("key-1"), headers.get("X-api-key"));
      assertEquals(List.of("Bearer token-1"), headers.get("Authorization"));
    } finally {
      wire.server().stop(0);
    }
  }

  @Test
  void blankBearerTokenSetsNoAuthorizationHeader() throws IOException {
    final var wire = startServer();
    try {
      final var properties = new Properties();
      properties.setProperty("endpoint", wire.endpoint());
      properties.setProperty("bearerToken", " ");
      final var client = WebhookConfig.parseConfig(properties)
          .createClientBuilder()
          .createClient();
      client.post("{}").join();

      assertNull(wire.requestHeaders().join().get("Authorization"));
    } finally {
      wire.server().stop(0);
    }
  }

  @Test
  void absentRequestTimeoutPreservesBuilderTimeout() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/notify");
    final var preset = WebhookClient.clientBuilder().requestTimeout(Duration.ofSeconds(30));
    final var builder = WebhookConfig.parseConfig(properties).createClientBuilder(preset);
    assertEquals(Duration.ofSeconds(30), builder.requestTimeout());
  }
}
