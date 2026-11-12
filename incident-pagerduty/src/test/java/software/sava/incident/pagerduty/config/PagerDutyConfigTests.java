package software.sava.incident.pagerduty.config;

import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyConfigTests {

  private static final String ROUTING_KEY_REQUIRED = "PagerdutyConfig routingKey is required.";

  @Test
  void parseMinimalConfig() {
    final var json = """
        {"routingKey":"test-routing-key"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.endpoint());
    assertNull(config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
  }

  @Test
  void parseFullConfigWithEndpointAndTimeout() {
    final var json = """
        {"routingKey":"test-routing-key","endpoint":"https://events.pagerduty.com","requestTimeout":"PT10S"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void sparseConfigPreservesBuilderSettings() {
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse("""
        {"routingKey":"rk"}"""));
    final var builder = PagerDutyEventClient.clientBuilder()
        .endpoint(URI.create("https://pre.example.com"))
        .requestTimeout(Duration.ofSeconds(3))
        .defaultRoutingKey("pre-rk");
    assertSame(builder, config.createClientBuilder(builder));
    // absent config values must not overwrite what the builder already holds; the routing
    // key is required, so the config's always replaces a pre-seeded one
    assertEquals("rk", builder.defaultRoutingKey());
    assertEquals(URI.create("https://pre.example.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(3), builder.requestTimeout());
  }

  @Test
  void parseConfigMissingRoutingKey() {
    final var json = """
        {"endpoint":"https://events.pagerduty.com"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
    assertEquals(ROUTING_KEY_REQUIRED, ex.getMessage());
  }

  @Test
  void parseConfigBlankRoutingKey() {
    final var json = """
        {"routingKey":"  ","endpoint":"https://events.pagerduty.com"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
    assertEquals(ROUTING_KEY_REQUIRED, ex.getMessage());
  }

  @Test
  void parseConfigUnknownField() {
    final var json = """
        {"routingKey":"test-routing-key","unknown":"value"}""";
    assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
  }

  @Test
  void aJsonConfigCarryingTheRemovedAuthTokenIsRefused() {
    // the key was removed with the Authorization header it fed: in JSON it is now an unknown
    // field like any other, so a stale config fails loudly instead of being silently ignored
    final var json = """
        {"routingKey":"test-routing-key","authToken":"test-auth-token"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
    assertEquals("Unknown HttpApiClientConfig field authToken", ex.getMessage());
  }

  @Test
  void parsePropertiesMinimalConfig() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    final var config = PagerDutyConfig.parseConfig(properties);
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.endpoint());
    assertNull(config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
  }

  @Test
  void parsePropertiesFullConfigWithEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    properties.setProperty("endpoint", "https://events.pagerduty.com");
    properties.setProperty("requestTimeout", "PT10S");
    final var config = PagerDutyConfig.parseConfig(properties);
    assertEquals("test-routing-key", config.routingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void parsePropertiesWithPrefixEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("pagerduty.endpoint", "https://events.pagerduty.com");
    properties.setProperty("pagerduty.requestTimeout", "PT10S");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty");
    assertEquals("test-routing-key", config.routingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void parsePropertiesMissingRoutingKey() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://events.pagerduty.com");
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(properties)
    );
    assertEquals(ROUTING_KEY_REQUIRED, ex.getMessage());
  }

  @Test
  void parsePropertiesBlankRoutingKey() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "  ");
    properties.setProperty("endpoint", "https://events.pagerduty.com");
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(properties)
    );
    assertEquals(ROUTING_KEY_REQUIRED, ex.getMessage());
  }

  @Test
  void parsePropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    // an unprefixed key must not be read when a prefix is given
    properties.setProperty("routingKey", "unprefixed-routing-key");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty");
    assertEquals("test-routing-key", config.routingKey());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
  }

  @Test
  void parsePropertiesWithDotSuffixedPrefix() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("routingKey", "unprefixed-routing-key");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty.");
    assertEquals("test-routing-key", config.routingKey());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
  }

  @Test
  void parsePropertiesWithBlankPrefix() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    final var config = PagerDutyConfig.parseConfig(properties, "  ");
    assertEquals("test-routing-key", config.routingKey());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
  }

  @Test
  void aPropertiesConfigCarryingTheRemovedAuthTokenParsesAndIgnoresIt() {
    // the Properties form reads only its known keys, so a stale authToken entry is ignored,
    // and the client built from the config sends no Authorization header
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("pagerduty.authToken", "test-auth-token");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty");
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.endpoint());
    assertNull(config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertNotNull(builder.createClient());
    final var request = builder.extendRequest()
        .apply(HttpRequest.newBuilder(URI.create("https://events.pagerduty.com/v2/enqueue")))
        .build();
    assertTrue(request.headers().firstValue("Authorization").isEmpty());
    assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
    assertEquals(Set.of("content-type"), request.headers().map().keySet().stream()
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet()));
  }
}
