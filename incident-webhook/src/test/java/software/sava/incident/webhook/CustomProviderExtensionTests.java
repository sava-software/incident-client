package software.sava.incident.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientException;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.webhook.config.WebhookConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.incident.core.json.JsonUtil.escapeJson;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Pins the third-party extension surface — everything here uses only public API, the
/// way a provider in another jar would: a custom [WebhookFormat] composing
/// [WebhookFormats#renderPlainText(IncidentAlert)], a stateless provider extending
/// [BaseWebhookIncidentClientFactory], a stateful provider composing
/// [WebhookConfig#parser()] for a field [WebhookConfig] does not carry, and a format class
/// overriding [WebhookFormat#retryAfter(String)] to read a product's own failure body.
final class CustomProviderExtensionTests {

  private static final IncidentAlert ALERT = IncidentAlert.build()
      .summary("summary-1")
      .severity(IncidentSeverity.CRITICAL)
      .create();

  /// A Discord-style stateless provider: format constant plus the public factory base.
  static final class ContentFactory extends BaseWebhookIncidentClientFactory {

    static final WebhookFormat CONTENT = alert -> String.format("""
        {"content":"%s"}""", escapeJson(WebhookFormats.renderPlainText(alert)));

    ContentFactory() {
      super("content");
    }

    @Override
    protected WebhookFormat format(final WebhookConfig config) {
      return CONTENT;
    }
  }

  /// A stateful provider needing a `roomId` beyond [WebhookConfig]: implements the SPI
  /// directly and composes [WebhookConfig#parser()] so endpoint/timeout/headers parsing
  /// and the strict unknown-field error stay shared.
  static final class RoomFactory implements IncidentClientFactory {

    @Override
    public String provider() {
      return "room";
    }

    @Override
    public IncidentClient createClient(final Properties properties, final String prefix) {
      final var parser = WebhookConfig.parser(prefix);
      parser.parseConfig(properties);
      final var roomId = properties.getProperty(parser.prefix() + "roomId");
      return createClient(parser.createConfig(), roomId);
    }

    @Override
    public IncidentClient createClient(final JsonIterator ji) {
      final var parser = WebhookConfig.parser();
      final var room = new Object() {
        String roomId;
      };
      ji.testObject((buf, offset, len, fieldJi) -> {
        if (fieldEquals("roomId", buf, offset, len)) {
          room.roomId = fieldJi.readString();
          return true;
        }
        return parser.test(buf, offset, len, fieldJi);
      });
      return createClient(parser.createConfig(), room.roomId);
    }

    private static IncidentClient createClient(final WebhookConfig config, final String roomId) {
      final WebhookFormat format = alert -> String.format("""
              {"room":"%s","content":"%s"}""",
          escapeJson(roomId), escapeJson(WebhookFormats.renderPlainText(alert))
      );
      return config.createClientBuilder().createClient().incidentClient(format);
    }
  }

  /// A product whose failures state the wait in the body, `{"wait_seconds":N}`: a lambda
  /// cannot override the hook, so the format is a class.
  static final class WaitingFormat implements WebhookFormat {

    @Override
    public String render(final IncidentAlert alert) {
      return String.format("""
          {"content":"%s"}""", escapeJson(WebhookFormats.renderPlainText(alert)));
    }

    @Override
    public Duration retryAfter(final String responseBody) {
      final var wait = new Object() {
        long seconds;
      };
      JsonIterator.parse(responseBody).testObject((buf, offset, len, fieldJi) -> {
        if (fieldEquals("wait_seconds", buf, offset, len)) {
          wait.seconds = fieldJi.readLong();
        } else {
          fieldJi.skip();
        }
        return true;
      });
      return wait.seconds > 0 ? Duration.ofSeconds(wait.seconds) : null;
    }
  }

  @Test
  void aCustomFormatReadsTheWaitFromItsFailureBody() throws IOException {
    final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        exchange.getRequestBody().readAllBytes();
        final var body = """
            {"error":"slow down","wait_seconds":12}""".getBytes(UTF_8);
        exchange.sendResponseHeaders(429, body.length);
        exchange.getResponseBody().write(body);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint("http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + "/hook")
          .createClient()
          .incidentClient(new WaitingFormat());
      final var ex = assertThrows(CompletionException.class, () -> client.reportIncident(ALERT).join());
      final var cause = assertInstanceOf(IncidentClientException.class, ex.getCause());
      assertEquals(429L, cause.errorCode());
      assertTrue(cause.canBeRetried());
      assertEquals(Duration.ofSeconds(12), cause.retryAfter());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void statelessProviderThroughTheFactoryBase() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/content");
    final var client = new ContentFactory().createClient(properties);
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertEquals(URI.create("https://hooks.example.com/content"), client.endpoint());
    assertEquals("""
            {"content":"[CRITICAL] summary-1"}""",
        webhookClient.format().render(ALERT)
    );
  }

  @Test
  void statefulProviderComposesTheConfigParserFromJson() {
    final var client = new RoomFactory().createClient(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/rooms","requestTimeout":"PT4S","roomId":"room-7"}"""));
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertEquals(URI.create("https://hooks.example.com/rooms"), client.endpoint());
    assertEquals("""
            {"room":"room-7","content":"[CRITICAL] summary-1"}""",
        webhookClient.format().render(ALERT)
    );
  }

  @Test
  void statefulProviderComposesTheConfigParserFromProperties() {
    final var properties = new Properties();
    properties.setProperty("incident.endpoint", "https://hooks.example.com/rooms");
    properties.setProperty("incident.requestTimeout", "4S");
    properties.setProperty("incident.roomId", "room-9");
    final var client = new RoomFactory().createClient(properties, "incident");
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertEquals("""
            {"room":"room-9","content":"[CRITICAL] summary-1"}""",
        webhookClient.format().render(ALERT)
    );
  }

  @Test
  void composedParserAppliesTimeoutToTheBuilder() {
    final var parser = WebhookConfig.parser("incident");
    final var properties = new Properties();
    properties.setProperty("incident.endpoint", "https://hooks.example.com/rooms");
    properties.setProperty("incident.requestTimeout", "4S");
    parser.parseConfig(properties);
    assertEquals("incident.", parser.prefix());
    final var builder = parser.createConfig().createClientBuilder();
    assertEquals(Duration.ofSeconds(4), builder.requestTimeout());
  }

  /// Delegation preserves the shared strictness: a field neither the custom factory nor
  /// [WebhookConfig] knows still fails at parse time.
  @Test
  void unknownFieldsStillFailThroughTheComposedParser() {
    assertThrows(IllegalStateException.class, () -> new RoomFactory().createClient(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/rooms","roomId":"room-7","bogus":1}""")));
  }

  @Test
  void composedParserStillRequiresEndpoint() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> new RoomFactory().createClient(JsonIterator.parse("""
            {"roomId":"room-7"}""")));
    assertEquals("WebhookConfig endpoint is required.", ex.getMessage());
  }
}
