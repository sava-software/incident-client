package software.sava.incident.io.config;

import org.junit.jupiter.api.Test;
import software.sava.incident.io.alert.AlertEvent;
import software.sava.incident.io.alert.AlertEventResponse;
import software.sava.incident.io.alert.IncidentIoAlertEventClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/// Pins [IncidentIoAlertSourceConfig]: its required and optional keys in both forms, and
/// that every value reaches the client builder or the adapter it configures.
final class IncidentIoAlertSourceConfigTests {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private record StubResponse(String status, String message, String deduplicationKey) implements AlertEventResponse {
  }

  private static final class StubClient implements IncidentIoAlertEventClient {

    private final List<AlertEvent> events = new ArrayList<>();

    @Override
    public CompletableFuture<AlertEventResponse> sendAlertEvent(final AlertEvent event) {
      events.add(event);
      return CompletableFuture.completedFuture(new StubResponse("stub-status", "stub-message", "stub-key"));
    }

    @Override
    public URI endpoint() {
      return URI.create("https://stub.example.com/alerts");
    }

    @Override
    public HttpClient httpClient() {
      return HTTP_CLIENT;
    }
  }

  private static HttpRequest decorated(final IncidentIoAlertEventClient.Builder builder) {
    return builder.extendRequest().apply(HttpRequest.newBuilder(URI.create("https://example.com/"))).build();
  }

  /// The title a resolve through the config's adapter sends.
  private static String resolveTitleSent(final IncidentIoAlertSourceConfig config) {
    final var stub = new StubClient();
    final var client = config.createIncidentClientBuilder(stub).createClient();
    assertEquals("stub-key", client.resolveIncident("key-1").join().key());
    return stub.events.getFirst().title();
  }

  @Test
  void parseFullJsonConfig() {
    final var config = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"secret-1","alertSourceConfigId":"src-1","resolveTitle":"Recovered",\
        "endpoint":"https://proxy.example.com/alerts","requestTimeout":"PT12S"}"""));
    assertEquals("secret-1", config.token());
    assertEquals("src-1", config.alertSourceConfigId());
    assertEquals("Recovered", config.resolveTitle());
    assertEquals(URI.create("https://proxy.example.com/alerts"), config.endpoint());
    assertEquals(Duration.ofSeconds(12), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals(URI.create("https://proxy.example.com/alerts"), builder.endpoint());
    assertEquals(Duration.ofSeconds(12), builder.requestTimeout());
    assertEquals("src-1", builder.alertSourceConfigId());
    final var request = decorated(builder);
    assertEquals(List.of("Bearer secret-1"), request.headers().allValues("Authorization"));
    assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
    assertEquals(URI.create("https://proxy.example.com/alerts"), builder.createClient().endpoint());
    assertEquals("Recovered", resolveTitleSent(config));
  }

  @Test
  void aMinimalConfigUsesTheDefaultEndpointForItsId() {
    final var config = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"secret-2","alertSourceConfigId":"src 2"}"""));
    assertNull(config.endpoint());
    assertNull(config.requestTimeout());
    assertNull(config.resolveTitle());

    final var builder = config.createClientBuilder();
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
    assertEquals("/v2/alert_events/http/src%202", builder.createClient().endpoint().getRawPath());
    // without a resolve title the adapter sends the key
    assertEquals("key-1", resolveTitleSent(config));
  }

  @Test
  void anEndpointMakesTheIdOptional() {
    final var config = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"t","endpoint":"https://proxy.example.com/a"}"""));
    assertNull(config.alertSourceConfigId());
    final var builder = config.createClientBuilder();
    assertNull(builder.alertSourceConfigId());
    assertEquals(URI.create("https://proxy.example.com/a"), builder.createClient().endpoint());

    final var blankId = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"t","endpoint":"https://proxy.example.com/a","alertSourceConfigId":" "}"""));
    assertNull(blankId.alertSourceConfigId(), "a blank id reads as unset");
  }

  @Test
  void sparseConfigPreservesBuilderSettings() {
    final var idOnly = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"t","alertSourceConfigId":"from-config"}"""));
    final var builder = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(URI.create("https://pre.example.com"))
        .requestTimeout(Duration.ofSeconds(3));
    assertSame(builder, idOnly.createClientBuilder(builder));
    assertEquals(URI.create("https://pre.example.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(3), builder.requestTimeout());
    assertEquals("from-config", builder.alertSourceConfigId());

    final var endpointOnly = IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
        {"token":"t","endpoint":"https://config.example.com"}"""));
    final var preset = IncidentIoAlertEventClient.clientBuilder().alertSourceConfigId("preset-id");
    endpointOnly.createClientBuilder(preset);
    assertEquals("preset-id", preset.alertSourceConfigId());
    assertEquals(URI.create("https://config.example.com"), preset.endpoint());
  }

  @Test
  void theTokenIsRequired() {
    for (final var json : new String[]{"""
        {"alertSourceConfigId":"src"}""", """
        {"token":" ","alertSourceConfigId":"src"}"""}) {
      final var thrown = assertThrows(IllegalStateException.class,
          () -> IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse(json)));
      assertEquals("IncidentIoAlertSourceConfig token is required.", thrown.getMessage());
    }
  }

  @Test
  void theIdIsRequiredWithoutAnEndpoint() {
    for (final var json : new String[]{"""
        {"token":"t"}""", """
        {"token":"t","alertSourceConfigId":" \\t"}"""}) {
      final var thrown = assertThrows(IllegalStateException.class,
          () -> IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse(json)));
      assertEquals("IncidentIoAlertSourceConfig alertSourceConfigId is required unless endpoint is set.",
          thrown.getMessage());
    }
  }

  @Test
  void anUnknownJsonFieldIsRefused() {
    final var thrown = assertThrows(IllegalStateException.class,
        () -> IncidentIoAlertSourceConfig.parseConfig(JsonIterator.parse("""
            {"token":"t","alertSourceConfigId":"src","bearerToken":"x"}""")));
    assertEquals("Unknown HttpApiClientConfig field bearerToken", thrown.getMessage());
  }

  @Test
  void parsePropertiesWithAPrefix() {
    for (final var prefix : new String[]{"incident", "incident."}) {
      final var properties = new Properties();
      properties.setProperty("incident.provider", "incident.io-alerts");
      properties.setProperty("incident.token", "secret-3");
      properties.setProperty("incident.alertSourceConfigId", "src-3");
      properties.setProperty("incident.resolveTitle", "Resolved by monitor");
      properties.setProperty("incident.endpoint", "https://proxy.example.com/b");
      properties.setProperty("incident.requestTimeout", "9S");
      properties.setProperty("incident.bearerToken", "ignored");
      properties.setProperty("token", "unprefixed-ignored");
      final var config = IncidentIoAlertSourceConfig.parseConfig(properties, prefix);
      assertEquals("secret-3", config.token());
      assertEquals("src-3", config.alertSourceConfigId());
      assertEquals("Resolved by monitor", config.resolveTitle());
      assertEquals(URI.create("https://proxy.example.com/b"), config.endpoint());
      assertEquals(Duration.ofSeconds(9), config.requestTimeout());
      assertEquals(List.of("Bearer secret-3"), decorated(config.createClientBuilder()).headers().allValues("Authorization"));
      assertEquals("Resolved by monitor", resolveTitleSent(config));
    }
  }

  @Test
  void parsePropertiesWithoutAPrefix() {
    final var properties = new Properties();
    properties.setProperty("token", "secret-4");
    properties.setProperty("alertSourceConfigId", "src-4");
    properties.setProperty("unrelated", "ignored");
    final var config = IncidentIoAlertSourceConfig.parseConfig(properties);
    assertEquals("secret-4", config.token());
    assertEquals("src-4", config.alertSourceConfigId());
    assertNull(config.resolveTitle());
    assertNull(config.endpoint());
    assertNull(config.requestTimeout());
    assertEquals("/v2/alert_events/http/src-4", config.createClientBuilder().createClient().endpoint().getRawPath());
  }

  @Test
  void propertiesRequireTheTokenAndTheIdUnlessAnEndpointIsSet() {
    final var noToken = new Properties();
    noToken.setProperty("alertSourceConfigId", "src");
    assertThrows(IllegalStateException.class, () -> IncidentIoAlertSourceConfig.parseConfig(noToken));

    final var noId = new Properties();
    noId.setProperty("token", "t");
    assertThrows(IllegalStateException.class, () -> IncidentIoAlertSourceConfig.parseConfig(noId));

    noId.setProperty("endpoint", "https://proxy.example.com/c");
    final var config = IncidentIoAlertSourceConfig.parseConfig(noId);
    assertNull(config.alertSourceConfigId());
    assertEquals(URI.create("https://proxy.example.com/c"), config.createClientBuilder().createClient().endpoint());
  }
}
