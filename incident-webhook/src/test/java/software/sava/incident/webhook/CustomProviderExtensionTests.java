package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentClientFactory;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.webhook.config.WebhookConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.incident.core.json.JsonUtil.escapeJson;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Pins the third-party extension surface — everything here uses only public API, the
/// way a provider in another jar would: a custom [WebhookFormat] composing
/// [WebhookFormats#renderPlainText(IncidentAlert)], a stateless provider extending
/// [BaseWebhookIncidentClientFactory], and a stateful provider composing
/// [WebhookConfig#parser()] for a field [WebhookConfig] does not carry.
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
