package software.sava.incident.io.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class IncidentIoConfigTests {

  @Test
  void parseFullConfig() {
    final var json = """
        {"bearerToken":"test-bearer-token"}""";
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-bearer-token", config.bearerToken());

    final var builder = config.createClientBuilder();
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parseFullConfigWithEndpointAndTimeout() {
    final var json = """
        {"bearerToken":"test-bearer-token","endpoint":"https://api.incident.io/v2/incidents","requestTimeout":"PT10S"}""";
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-bearer-token", config.bearerToken());
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parseConfigMissingBearerToken() {
    final var json = """
        {}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(JsonIterator.parse(json)));
    assertTrue(ex.getMessage().contains("bearerToken"));
  }

  @Test
  void parseConfigBlankBearerToken() {
    final var json = """
        {"bearerToken":"  "}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(JsonIterator.parse(json)));
    assertTrue(ex.getMessage().contains("bearerToken"));
  }

  @Test
  void parseConfigUnknownField() {
    final var json = """
        {"bearerToken":"test-bearer-token","unknown":"value"}""";
    assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(JsonIterator.parse(json)));
  }

  @Test
  void parsePropertiesFullConfig() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "test-bearer-token");
    final var config = IncidentIoConfig.parseConfig(properties);
    assertEquals("test-bearer-token", config.bearerToken());

    final var builder = config.createClientBuilder();
    assertNull(builder.endpoint());
    assertNull(builder.requestTimeout());
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parsePropertiesFullConfigWithEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "test-bearer-token");
    properties.setProperty("endpoint", "https://api.incident.io/v2/incidents");
    properties.setProperty("requestTimeout", "PT10S");
    final var config = IncidentIoConfig.parseConfig(properties);
    assertEquals("test-bearer-token", config.bearerToken());
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parsePropertiesWithPrefixEndpointAndTimeout() {
    final var properties = new Properties();
    properties.setProperty("incidentio.bearerToken", "test-bearer-token");
    properties.setProperty("incidentio.endpoint", "https://api.incident.io/v2/incidents");
    properties.setProperty("incidentio.requestTimeout", "PT10S");
    final var config = IncidentIoConfig.parseConfig(properties, "incidentio");
    assertEquals("test-bearer-token", config.bearerToken());
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), config.endpoint());
    assertEquals(Duration.ofSeconds(10), config.requestTimeout());

    final var builder = config.createClientBuilder();
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), builder.endpoint());
    assertEquals(Duration.ofSeconds(10), builder.requestTimeout());
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parsePropertiesMissingBearerToken() {
    final var properties = new Properties();
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(properties));
    assertTrue(ex.getMessage().contains("bearerToken"));
  }

  @Test
  void parsePropertiesBlankBearerToken() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "  ");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(properties));
    assertTrue(ex.getMessage().contains("bearerToken"));
  }

  @Test
  void parsePropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("incidentio.bearerToken", "test-bearer-token");
    final var config = IncidentIoConfig.parseConfig(properties, "incidentio");
    assertEquals("test-bearer-token", config.bearerToken());

    final var builder = config.createClientBuilder();
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parsePropertiesWithDotSuffixedPrefix() {
    final var properties = new Properties();
    properties.setProperty("incidentio.bearerToken", "test-bearer-token");
    final var config = IncidentIoConfig.parseConfig(properties, "incidentio.");
    assertEquals("test-bearer-token", config.bearerToken());

    final var builder = config.createClientBuilder();
    assertNotNull(builder.extendRequest());
  }

  @Test
  void parsePropertiesWithBlankPrefix() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "test-bearer-token");
    final var config = IncidentIoConfig.parseConfig(properties, "  ");
    assertEquals("test-bearer-token", config.bearerToken());

    final var builder = config.createClientBuilder();
    assertNotNull(builder.extendRequest());
  }
}
