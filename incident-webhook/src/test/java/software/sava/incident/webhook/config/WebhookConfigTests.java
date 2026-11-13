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
    properties.setProperty("incident.messageThreadId", "42");
    final var config = WebhookConfig.parseConfig(properties, "incident");

    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(13), config.requestTimeout());
    assertEquals("token-1", config.bearerToken());
    assertEquals("-100123", config.chatId());
    assertEquals(42, config.messageThreadId());
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
    assertEquals(0, config.messageThreadId());
    assertEquals(Map.of(), config.headers());
  }

  @Test
  void parseFromJsonPreservesHeaderOrder() {
    final var config = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","requestTimeout":"PT5S",
        "bearerToken":"token-2","chatId":"@channel","messageThreadId":42,"headers":{"Z-First":"z","A-Second":"a"}}"""));
    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
    assertEquals("token-2", config.bearerToken());
    assertEquals("@channel", config.chatId());
    assertEquals(42, config.messageThreadId());
    assertEquals(List.of("Z-First", "A-Second"), List.copyOf(config.headers().keySet()));
    assertEquals(Map.of("Z-First", "z", "A-Second", "a"), config.headers());
  }

  private static int propertiesThreadId(final String prefix, final String value) {
    final var properties = new Properties();
    final var keyPrefix = prefix == null ? "" : prefix + '.';
    properties.setProperty(keyPrefix + "endpoint", "https://hooks.example.com/notify");
    if (value != null) {
      properties.setProperty(keyPrefix + "messageThreadId", value);
    }
    return WebhookConfig.parseConfig(properties, prefix).messageThreadId();
  }

  private static int jsonThreadId(final String value) {
    return WebhookConfig.parseConfig(JsonIterator.parse(
        "{\"endpoint\":\"https://hooks.example.com/notify\",\"messageThreadId\":" + value + '}'
    )).messageThreadId();
  }

  private static void assertInvalidProperty(final String prefix, final String value) {
    final var ex = assertThrows(IllegalStateException.class, () -> propertiesThreadId(prefix, value));
    assertEquals("WebhookConfig messageThreadId must be an integer from 0 to 2147483647: '" + value + "'.",
        ex.getMessage());
  }

  @Test
  void messageThreadIdFromProperties() {
    for (final var prefix : new String[]{null, "incident"}) {
      assertEquals(0, propertiesThreadId(prefix, null), "absent");
      assertEquals(0, propertiesThreadId(prefix, ""), "empty");
      assertEquals(0, propertiesThreadId(prefix, " "), "blank");
      assertEquals(0, propertiesThreadId(prefix, "0"));
      assertEquals(1, propertiesThreadId(prefix, "1"));
      assertEquals(9, propertiesThreadId(prefix, "9"));
      assertEquals(40_961, propertiesThreadId(prefix, "40961"));
      assertEquals(7, propertiesThreadId(prefix, "007"));
      assertEquals(Integer.MAX_VALUE, propertiesThreadId(prefix, "2147483647"));
    }
  }

  @Test
  void anInvalidMessageThreadIdPropertyFails() {
    for (final var prefix : new String[]{null, "incident"}) {
      assertInvalidProperty(prefix, "topic");
      assertInvalidProperty(prefix, "-1");
      assertInvalidProperty(prefix, "-0");
      assertInvalidProperty(prefix, "+5");
      assertInvalidProperty(prefix, "2147483648");
      assertInvalidProperty(prefix, "99999999999999999999");
      assertInvalidProperty(prefix, "1.5");
      assertInvalidProperty(prefix, "1e3");
      assertInvalidProperty(prefix, " 5");
      assertInvalidProperty(prefix, "5 ");
      assertInvalidProperty(prefix, "/");
      assertInvalidProperty(prefix, ":");
      // Integer.parseInt would take these Arabic-Indic digits as 12
      assertInvalidProperty(prefix, "\u0661\u0662");
    }
  }

  @Test
  void messageThreadIdFromJson() {
    assertEquals(0, WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify"}""")).messageThreadId(), "absent");
    assertEquals(0, jsonThreadId("null"));
    assertEquals(0, jsonThreadId("0"));
    assertEquals(1, jsonThreadId("1"));
    assertEquals(40_961, jsonThreadId("40961"));
    assertEquals(Integer.MAX_VALUE, jsonThreadId("2147483647"));
  }

  @Test
  void anInvalidMessageThreadIdJsonNumberFails() {
    for (final var value : new String[]{"-1", "2147483648", "99999999999999999999", "1.5", "1.0", "1e3"}) {
      final var ex = assertThrows(IllegalStateException.class, () -> jsonThreadId(value), value);
      assertEquals("WebhookConfig messageThreadId must be an integer from 0 to 2147483647: '" + value + "'.",
          ex.getMessage());
    }
  }

  @Test
  void aMessageThreadIdThatIsNotAJsonNumberFails() {
    final var string = assertThrows(IllegalStateException.class, () -> jsonThreadId("\"5\""));
    assertEquals("WebhookConfig messageThreadId must be a JSON number, not the string '5'.", string.getMessage());
    final var bool = assertThrows(IllegalStateException.class, () -> jsonThreadId("true"));
    assertEquals("WebhookConfig messageThreadId must be a JSON number, not BOOLEAN.", bool.getMessage());
    final var object = assertThrows(IllegalStateException.class, () -> jsonThreadId("{}"));
    assertEquals("WebhookConfig messageThreadId must be a JSON number, not OBJECT.", object.getMessage());
    final var array = assertThrows(IllegalStateException.class, () -> jsonThreadId("[1]"));
    assertEquals("WebhookConfig messageThreadId must be a JSON number, not ARRAY.", array.getMessage());
  }

  /// A null token is skipped, so the fields after it still parse.
  @Test
  void aNullMessageThreadIdKeepsTheFieldsAfterIt() {
    final var config = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","messageThreadId":null,"chatId":"@channel"}"""));
    assertEquals(0, config.messageThreadId());
    assertEquals("@channel", config.chatId());
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
