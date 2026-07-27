package software.sava.incident.webhook.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class WebhookConfigTests {

  @Test
  void parseFromPropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("incident.endpoint", "https://hooks.example.com/notify");
    properties.setProperty("incident.requestTimeout", "13S");
    properties.setProperty("incident.bearerToken", "token-1");
    properties.setProperty("incident.headers.X-Two", "two");
    properties.setProperty("incident.headers.X-One", "one");
    properties.setProperty("incident.chatId", "-100123");
    final var config = WebhookConfig.parseConfig(properties, "incident");

    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(13), config.requestTimeout());
    assertEquals("token-1", config.bearerToken());
    assertEquals("-100123", config.chatId());
    // properties headers are sorted by name for a deterministic order
    assertEquals(List.of("X-One", "X-Two"), List.copyOf(config.headers().keySet()));
    assertEquals(Map.of("X-One", "one", "X-Two", "two"), config.headers());
  }

  @Test
  void prefixDotIsOptional() {
    // both spellings normalize to the dot-terminated prefix composing factories read
    assertEquals("incident.", WebhookConfig.parser("incident").prefix());
    assertEquals("incident.", WebhookConfig.parser("incident.").prefix());
    assertEquals("", WebhookConfig.parser(null).prefix());

    final var properties = new Properties();
    properties.setProperty("incident.endpoint", "https://hooks.example.com/notify");
    final var config = WebhookConfig.parseConfig(properties, "incident.");
    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertNull(config.bearerToken());
    assertNull(config.chatId());
    assertEquals(Map.of(), config.headers());
  }

  @Test
  void parseFromJsonPreservesHeaderOrder() {
    final var config = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","requestTimeout":"PT5S",
        "bearerToken":"token-2","chatId":"@channel","headers":{"Z-First":"z","A-Second":"a"}}"""));
    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
    assertEquals("token-2", config.bearerToken());
    assertEquals("@channel", config.chatId());
    assertEquals(List.of("Z-First", "A-Second"), List.copyOf(config.headers().keySet()));
    assertEquals(Map.of("Z-First", "z", "A-Second", "a"), config.headers());
  }

  @Test
  void headersAreImmutableAtEverySize() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/notify");
    properties.setProperty("headers.X-One", "one");
    final var single = WebhookConfig.parseConfig(properties);
    assertThrows(UnsupportedOperationException.class, () -> single.headers().put("X-Two", "two"));

    properties.setProperty("headers.X-Two", "two");
    final var multiple = WebhookConfig.parseConfig(properties);
    assertThrows(UnsupportedOperationException.class, () -> multiple.headers().put("X-Three", "three"));
  }

  @Test
  void blankHeaderNamesAreSkipped() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/notify");
    properties.setProperty("headers.", "no-name");
    properties.setProperty("headers.X-One", "one");
    final var config = WebhookConfig.parseConfig(properties);
    assertEquals(Map.of("X-One", "one"), config.headers());
  }

  @Test
  void missingEndpointFails() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> WebhookConfig.parseConfig(new Properties()));
    assertEquals("WebhookConfig endpoint is required.", ex.getMessage());

    assertThrows(IllegalStateException.class,
        () -> WebhookConfig.parseConfig(JsonIterator.parse("""
            {"bearerToken":"token-1"}""")));
  }

  @Test
  void unknownJsonFieldFails() {
    assertThrows(IllegalStateException.class,
        () -> WebhookConfig.parseConfig(JsonIterator.parse("""
            {"endpoint":"https://hooks.example.com/notify","unknown":1}""")));
  }

  @Test
  void createClientBuilderAppliesConfig() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/notify");
    properties.setProperty("requestTimeout", "3S");
    final var builder = WebhookConfig.parseConfig(properties).createClientBuilder();
    assertEquals(URI.create("https://hooks.example.com/notify"), builder.endpoint());
    assertEquals(Duration.ofSeconds(3), builder.requestTimeout());
  }
}
