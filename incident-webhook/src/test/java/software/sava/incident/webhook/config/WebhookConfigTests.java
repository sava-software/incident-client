package software.sava.incident.webhook.config;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.incident.core.api.IncidentSeverity.*;

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
    properties.setProperty("incident.silentSeverities", "WARNING,INFO");
    final var config = WebhookConfig.parseConfig(properties, "incident");

    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(13), config.requestTimeout());
    assertEquals("token-1", config.bearerToken());
    assertEquals("-100123", config.chatId());
    assertEquals(42, config.messageThreadId());
    assertEquals(Set.of(INFO, WARNING), config.silentSeverities());
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
    assertEquals(Set.of(), config.silentSeverities());
    assertEquals(Map.of(), config.headers());
  }

  @Test
  void parseFromJsonPreservesHeaderOrder() {
    final var config = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","requestTimeout":"PT5S",
        "bearerToken":"token-2","chatId":"@channel","messageThreadId":42,"silentSeverities":["ERROR"],"headers":{"Z-First":"z","A-Second":"a"}}"""));
    assertEquals(URI.create("https://hooks.example.com/notify"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
    assertEquals("token-2", config.bearerToken());
    assertEquals("@channel", config.chatId());
    assertEquals(42, config.messageThreadId());
    assertEquals(Set.of(ERROR), config.silentSeverities());
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

  private static Set<IncidentSeverity> propertiesSilentSeverities(final String prefix, final String value) {
    final var properties = new Properties();
    final var keyPrefix = prefix == null ? "" : prefix + '.';
    properties.setProperty(keyPrefix + "endpoint", "https://hooks.example.com/notify");
    if (value != null) {
      properties.setProperty(keyPrefix + "silentSeverities", value);
    }
    return WebhookConfig.parseConfig(properties, prefix).silentSeverities();
  }

  private static Set<IncidentSeverity> jsonSilentSeverities(final String value) {
    return WebhookConfig.parseConfig(JsonIterator.parse(
        "{\"endpoint\":\"https://hooks.example.com/notify\",\"silentSeverities\":" + value + ",\"chatId\":\"@channel\"}"
    )).silentSeverities();
  }

  @Test
  void silentSeveritiesFromProperties() {
    for (final var prefix : new String[]{null, "incident"}) {
      assertEquals(Set.of(), propertiesSilentSeverities(prefix, null), "absent");
      assertEquals(Set.of(), propertiesSilentSeverities(prefix, ""), "empty");
      assertEquals(Set.of(), propertiesSilentSeverities(prefix, " "), "blank");
      assertEquals(Set.of(INFO), propertiesSilentSeverities(prefix, "INFO"));
      assertEquals(Set.of(INFO, WARNING), propertiesSilentSeverities(prefix, "INFO,WARNING"));
      assertEquals(Set.of(INFO, WARNING), propertiesSilentSeverities(prefix, " INFO , WARNING "));
      assertEquals(Set.of(INFO), propertiesSilentSeverities(prefix, "INFO,INFO"));
      assertEquals(EnumSet.allOf(IncidentSeverity.class),
          propertiesSilentSeverities(prefix, "INFO,WARNING,ERROR,CRITICAL"));
      assertEquals(Set.of(CRITICAL), propertiesSilentSeverities(prefix, "CRITICAL"));
    }
  }

  private static void assertInvalidSilentSeveritiesProperty(final String prefix, final String value) {
    final var ex = assertThrows(IllegalStateException.class, () -> propertiesSilentSeverities(prefix, value), value);
    assertEquals(
        "WebhookConfig silentSeverities must name severities from [CRITICAL, ERROR, WARNING, INFO]: '" + value + "'.",
        ex.getMessage()
    );
  }

  @Test
  void anInvalidSilentSeveritiesPropertyFails() {
    for (final var prefix : new String[]{null, "incident"}) {
      assertInvalidSilentSeveritiesProperty(prefix, "info");
      assertInvalidSilentSeveritiesProperty(prefix, "INFO,");
      assertInvalidSilentSeveritiesProperty(prefix, ",INFO");
      assertInvalidSilentSeveritiesProperty(prefix, "INFO,,WARNING");
      assertInvalidSilentSeveritiesProperty(prefix, ",");
      assertInvalidSilentSeveritiesProperty(prefix, "BOGUS");
      assertInvalidSilentSeveritiesProperty(prefix, "INFO WARNING");
      assertInvalidSilentSeveritiesProperty(prefix, "INFO;WARNING");
      // the whole value, not the bad entry, is named
      assertInvalidSilentSeveritiesProperty(prefix, "INFO, bogus");
    }
  }

  @Test
  void silentSeveritiesFromJson() {
    assertEquals(Set.of(), WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify"}""")).silentSeverities(), "absent");
    assertEquals(Set.of(), jsonSilentSeverities("null"));
    assertEquals(Set.of(), jsonSilentSeverities("[]"));
    assertEquals(Set.of(), jsonSilentSeverities("[ ]"));
    assertEquals(Set.of(INFO), jsonSilentSeverities("[\"INFO\"]"));
    assertEquals(Set.of(INFO, WARNING), jsonSilentSeverities("[\"INFO\",\"WARNING\"]"));
    assertEquals(Set.of(INFO), jsonSilentSeverities("[\"INFO\",\"INFO\"]"));
    assertEquals(EnumSet.allOf(IncidentSeverity.class),
        jsonSilentSeverities("[\"CRITICAL\",\"ERROR\",\"WARNING\",\"INFO\"]"));
  }

  /// The array is read whole, so the fields after it, populated or empty, still parse.
  @Test
  void silentSeveritiesKeepTheFieldsAfterThem() {
    final var populated = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":["INFO","WARNING"],"chatId":"@channel","messageThreadId":7}"""));
    assertEquals(Set.of(INFO, WARNING), populated.silentSeverities());
    assertEquals("@channel", populated.chatId());
    assertEquals(7, populated.messageThreadId());
    final var empty = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":[],"chatId":"@channel","messageThreadId":7}"""));
    assertEquals(Set.of(), empty.silentSeverities());
    assertEquals("@channel", empty.chatId());
    assertEquals(7, empty.messageThreadId());
  }

  /// A repeated key's last occurrence decides, as for every other field.
  @Test
  void aRepeatedSilentSeveritiesKeyTakesTheLast() {
    assertEquals(Set.of(WARNING), WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":["INFO"],"silentSeverities":["WARNING"]}"""))
        .silentSeverities());
    assertEquals(Set.of(), WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":["INFO"],"silentSeverities":null}"""))
        .silentSeverities());
    assertEquals(Set.of(), WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":["INFO"],"silentSeverities":[]}"""))
        .silentSeverities());
  }

  @Test
  void aSilentSeveritiesThatIsNotAJsonArrayFails() {
    final var string = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("\"INFO\""));
    assertEquals("WebhookConfig silentSeverities must be a JSON array of severity names, not STRING.",
        string.getMessage());
    final var number = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("1"));
    assertEquals("WebhookConfig silentSeverities must be a JSON array of severity names, not NUMBER.",
        number.getMessage());
    final var object = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("{}"));
    assertEquals("WebhookConfig silentSeverities must be a JSON array of severity names, not OBJECT.",
        object.getMessage());
    final var bool = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("true"));
    assertEquals("WebhookConfig silentSeverities must be a JSON array of severity names, not BOOLEAN.",
        bool.getMessage());
  }

  @Test
  void anInvalidSilentSeverityInJsonFails() {
    final var lowerCase = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("[\"info\"]"));
    assertEquals("WebhookConfig silentSeverities must name severities from [CRITICAL, ERROR, WARNING, INFO]: 'info'.",
        lowerCase.getMessage());
    // a JSON element is never stripped, and the offending element alone is named
    final var padded = assertThrows(IllegalStateException.class,
        () -> jsonSilentSeverities("[\"INFO\",\" WARNING\"]"));
    assertEquals("WebhookConfig silentSeverities must name severities from [CRITICAL, ERROR, WARNING, INFO]: ' WARNING'.",
        padded.getMessage());
    final var empty = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("[\"\"]"));
    assertEquals("WebhookConfig silentSeverities must name severities from [CRITICAL, ERROR, WARNING, INFO]: ''.",
        empty.getMessage());
    final var number = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("[1]"));
    assertEquals("WebhookConfig silentSeverities must hold severity names, not NUMBER.", number.getMessage());
    final var nullElement = assertThrows(IllegalStateException.class,
        () -> jsonSilentSeverities("[\"INFO\",null]"));
    assertEquals("WebhookConfig silentSeverities must hold severity names, not NULL.", nullElement.getMessage());
    final var nested = assertThrows(IllegalStateException.class, () -> jsonSilentSeverities("[[\"INFO\"]]"));
    assertEquals("WebhookConfig silentSeverities must hold severity names, not ARRAY.", nested.getMessage());
  }

  /// A null token is skipped, so the fields after it still parse.
  @Test
  void aNullSilentSeveritiesKeepsTheFieldsAfterIt() {
    final var config = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":null,"chatId":"@channel"}"""));
    assertEquals(Set.of(), config.silentSeverities());
    assertEquals("@channel", config.chatId());
  }

  @Test
  void silentSeveritiesAreImmutable() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://hooks.example.com/notify");
    final var unconfigured = WebhookConfig.parseConfig(properties);
    assertThrows(UnsupportedOperationException.class, () -> unconfigured.silentSeverities().add(INFO));

    properties.setProperty("silentSeverities", "INFO");
    final var configured = WebhookConfig.parseConfig(properties);
    assertThrows(UnsupportedOperationException.class, () -> configured.silentSeverities().add(WARNING));
    assertThrows(UnsupportedOperationException.class, () -> configured.silentSeverities().remove(INFO));

    final var json = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify","silentSeverities":["INFO"]}"""));
    assertThrows(UnsupportedOperationException.class, () -> json.silentSeverities().add(WARNING));
    final var jsonUnconfigured = WebhookConfig.parseConfig(JsonIterator.parse("""
        {"endpoint":"https://hooks.example.com/notify"}"""));
    assertThrows(UnsupportedOperationException.class, () -> jsonUnconfigured.silentSeverities().add(WARNING));
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
