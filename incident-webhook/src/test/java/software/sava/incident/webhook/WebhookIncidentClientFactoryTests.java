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
  void createTelegramFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"telegram","config":{"endpoint":"https://api.telegram.org/bot123:ABC/sendMessage","chatId":"-100123"}}"""));
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    final var format = assertInstanceOf(TelegramTextFormat.class, webhookClient.format());
    assertEquals("-100123", format.chatId());
    assertEquals(0, format.messageThreadId());
    assertEquals(URI.create("https://api.telegram.org/bot123:ABC/sendMessage"), client.endpoint());
  }

  private static final IncidentAlert TOPIC_ALERT = IncidentAlert.build()
      .summary("summary-1")
      .severity(IncidentSeverity.INFO)
      .create();

  @Test
  void telegramWithATopicFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"telegram","config":{"endpoint":"https://api.telegram.org/bot123:ABC/sendMessage","chatId":"-100123","messageThreadId":42}}"""));
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    final var format = assertInstanceOf(TelegramTextFormat.class, webhookClient.format());
    assertEquals(new TelegramTextFormat("-100123", 42), format);
    assertEquals("""
        {"chat_id":"-100123","message_thread_id":42,"text":"[INFO] summary-1"}""", format.render(TOPIC_ALERT));
  }

  @Test
  void telegramWithATopicFromProperties() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "telegram");
    properties.setProperty("incident.endpoint", "https://api.telegram.org/bot123:ABC/sendMessage");
    properties.setProperty("incident.chatId", "@channel");
    properties.setProperty("incident.messageThreadId", "40961");
    final var client = IncidentClients.createClient(properties, "incident");
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    final var format = assertInstanceOf(TelegramTextFormat.class, webhookClient.format());
    assertEquals(new TelegramTextFormat("@channel", 40_961), format);
    assertEquals("""
        {"chat_id":"@channel","message_thread_id":40961,"text":"[INFO] summary-1"}""", format.render(TOPIC_ALERT));
  }

  @Test
  void telegramWithoutATopicSendsTheChatBody() {
    final var properties = new Properties();
    properties.setProperty("provider", "telegram");
    properties.setProperty("endpoint", "https://api.telegram.org/bot123:ABC/sendMessage");
    properties.setProperty("chatId", "@channel");
    final var client = IncidentClients.createClient(properties);
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    final var format = assertInstanceOf(TelegramTextFormat.class, webhookClient.format());
    assertEquals(new TelegramTextFormat("@channel", 0), format);
    assertEquals("""
        {"chat_id":"@channel","text":"[INFO] summary-1"}""", format.render(TOPIC_ALERT));
  }

  /// The topic is a field of the shared config that only `telegram` reads: another provider
  /// builds its own format with a valid one present, yet the shared parse still refuses an
  /// invalid one, whichever provider is configured.
  @Test
  void anotherProviderIgnoresTheTopic() {
    final var properties = new Properties();
    properties.setProperty("provider", "slack");
    properties.setProperty("endpoint", "https://hooks.slack.com/services/T000/B000/secret");
    properties.setProperty("chatId", "@channel");
    properties.setProperty("messageThreadId", "42");
    final var slack = assertInstanceOf(WebhookIncidentClient.class, IncidentClients.createClient(properties));
    assertSame(WebhookFormats.SLACK_TEXT, slack.format());

    properties.setProperty("provider", "webhook");
    final var webhook = assertInstanceOf(WebhookIncidentClient.class, IncidentClients.createClient(properties));
    assertSame(WebhookFormats.GENERIC_JSON, webhook.format());

    properties.setProperty("messageThreadId", "topic");
    final var ex = assertThrows(IllegalStateException.class, () -> IncidentClients.createClient(properties));
    assertEquals("WebhookConfig messageThreadId must be an integer from 0 to 2147483647: 'topic'.", ex.getMessage());
  }

  @Test
  void telegramFromProperties() {
    final var properties = new Properties();
    properties.setProperty("provider", "Telegram");
    properties.setProperty("endpoint", "https://api.telegram.org/bot123:ABC/sendMessage");
    properties.setProperty("chatId", "@channel");
    final var client = IncidentClients.createClient(properties);
    final var webhookClient = assertInstanceOf(WebhookIncidentClient.class, client);
    assertEquals("@channel", assertInstanceOf(TelegramTextFormat.class, webhookClient.format()).chatId());
  }

  @Test
  void telegramMissingChatIdFails() {
    final var properties = new Properties();
    properties.setProperty("provider", "telegram");
    properties.setProperty("endpoint", "https://api.telegram.org/bot123:ABC/sendMessage");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    assertEquals("WebhookConfig chatId is required for the telegram provider.", ex.getMessage());
  }

  @Test
  void telegramBlankChatIdFails() {
    final var properties = new Properties();
    properties.setProperty("provider", "telegram");
    properties.setProperty("endpoint", "https://api.telegram.org/bot123:ABC/sendMessage");
    properties.setProperty("chatId", " ");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    // the factory's config-flavored message, not TelegramTextFormat's own validation
    assertEquals("WebhookConfig chatId is required for the telegram provider.", ex.getMessage());
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
