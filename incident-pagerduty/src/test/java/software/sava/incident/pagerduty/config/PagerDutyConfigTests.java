package software.sava.incident.pagerduty.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyConfigTests {

  @Test
  void parseFullConfig() {
    final var json = """
        {"routingKey":"test-routing-key","authToken":"test-auth-token"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
  }

  @Test
  void parseFullConfigWithEndpointAndTimeout() {
    final var json = """
        {"routingKey":"test-routing-key","authToken":"test-auth-token","endpoint":"https://events.pagerduty.com","requestTimeout":"PT10S"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void parseConfigWithoutAuthToken() {
    final var json = """
        {"routingKey":"test-routing-key"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertNull(builder.authToken());
  }

  @Test
  void parseConfigMissingRoutingKey() {
    final var json = """
        {"authToken":"test-auth-token"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void parseConfigBlankRoutingKey() {
    final var json = """
        {"routingKey":"  ","authToken":"test-auth-token"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json))
    );
    assertTrue(ex.getMessage().contains("routingKey"));
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
  void parsePropertiesFullConfig() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    properties.setProperty("authToken", "test-auth-token");
    final var config = PagerDutyConfig.parseConfig(properties);
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
  }

  @Test
  void parsePropertiesFullConfigWithEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    properties.setProperty("authToken", "test-auth-token");
    properties.setProperty("endpoint", "https://events.pagerduty.com");
    properties.setProperty("requestTimeout", "PT10S");
    final var config = PagerDutyConfig.parseConfig(properties);
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void parsePropertiesWithPrefixEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("pagerduty.authToken", "test-auth-token");
    properties.setProperty("pagerduty.endpoint", "https://events.pagerduty.com");
    properties.setProperty("pagerduty.requestTimeout", "PT10S");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty");
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
    assertEquals(URI.create("https://events.pagerduty.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
  }

  @Test
  void parsePropertiesWithoutAuthToken() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    final var config = PagerDutyConfig.parseConfig(properties);
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertNull(builder.authToken());
  }

  @Test
  void parsePropertiesMissingRoutingKey() {
    final var properties = new Properties();
    properties.setProperty("authToken", "test-auth-token");
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(properties)
    );
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void parsePropertiesBlankRoutingKey() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "  ");
    properties.setProperty("authToken", "test-auth-token");
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(properties)
    );
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void parsePropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("pagerduty.authToken", "test-auth-token");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty");
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
  }

  @Test
  void parsePropertiesWithDotSuffixedPrefix() {
    final var properties = new Properties();
    properties.setProperty("pagerduty.routingKey", "test-routing-key");
    properties.setProperty("pagerduty.authToken", "test-auth-token");
    final var config = PagerDutyConfig.parseConfig(properties, "pagerduty.");
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
  }

  @Test
  void parsePropertiesWithBlankPrefix() {
    final var properties = new Properties();
    properties.setProperty("routingKey", "test-routing-key");
    properties.setProperty("authToken", "test-auth-token");
    final var config = PagerDutyConfig.parseConfig(properties, "  ");
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());

    final var builder = config.createClientBuilder();
    assertEquals("test-routing-key", builder.defaultRoutingKey());
    assertEquals("test-auth-token", builder.authToken());
  }
}
