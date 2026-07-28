package software.sava.incident.io.config;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.io.CreateIncidentRequest;
import software.sava.incident.io.IncidentIoClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
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
  void sparseConfigPreservesBuilderSettings() {
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t"}"""));
    final var builder = IncidentIoClient.clientBuilder()
        .endpoint(URI.create("https://pre.example.com"))
        .requestTimeout(Duration.ofSeconds(3));
    config.createClientBuilder(builder);
    // absent config values must not overwrite what the builder already holds
    assertEquals(URI.create("https://pre.example.com"), builder.endpoint());
    assertEquals(Duration.ofSeconds(3), builder.requestTimeout());
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
  void parseJsonIncidentClientMapping() {
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t","severityIds":{"CRITICAL":"sev-1","error":"sev-2"},
        "incidentTypeId":"type-1","statusId":"status-1","visibility":"private","mode":"retrospective",
        "incidentTimestampId":"ts-1"}"""));
    assertEquals(Map.of(
        IncidentSeverity.CRITICAL, "sev-1",
        IncidentSeverity.ERROR, "sev-2"
    ), config.severityIds());
    assertEquals("type-1", config.incidentTypeId());
    assertEquals("status-1", config.statusId());
    assertEquals(CreateIncidentRequest.Visibility.PRIVATE, config.visibility());
    assertEquals(CreateIncidentRequest.Mode.retrospective, config.mode());
    assertEquals("ts-1", config.incidentTimestampId());
  }

  @Test
  void parsePropertiesIncidentClientMapping() {
    final var properties = new Properties();
    properties.setProperty("io.bearerToken", "t");
    properties.setProperty("io.severityIds.CRITICAL", "sev-1");
    properties.setProperty("io.severityIds.WARNING", "sev-3");
    properties.setProperty("io.incidentTypeId", "type-1");
    properties.setProperty("io.statusId", "status-1");
    properties.setProperty("io.visibility", "public");
    properties.setProperty("io.mode", "TEST");
    properties.setProperty("io.incidentTimestampId", "ts-1");
    final var config = IncidentIoConfig.parseConfig(properties, "io");
    assertEquals(Map.of(
        IncidentSeverity.CRITICAL, "sev-1",
        IncidentSeverity.WARNING, "sev-3"
    ), config.severityIds());
    assertEquals("type-1", config.incidentTypeId());
    assertEquals("status-1", config.statusId());
    assertEquals(CreateIncidentRequest.Visibility.PUBLIC, config.visibility());
    assertEquals(CreateIncidentRequest.Mode.test, config.mode());
    assertEquals("ts-1", config.incidentTimestampId());
  }

  @Test
  void blankMappingPropertiesParseAsAbsent() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "t");
    properties.setProperty("severityIds.CRITICAL", "  ");
    properties.setProperty("visibility", "  ");
    properties.setProperty("mode", "");
    final var config = IncidentIoConfig.parseConfig(properties);
    assertTrue(config.severityIds().isEmpty());
    assertNull(config.visibility());
    assertNull(config.mode());
  }

  @Test
  void unknownEnumValuesFailLoudly() {
    final var properties = new Properties();
    properties.setProperty("bearerToken", "t");
    properties.setProperty("visibility", "internal");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(properties));
    assertTrue(ex.getMessage().contains("internal"));

    final var jsonEx = assertThrows(IllegalStateException.class,
        () -> IncidentIoConfig.parseConfig(JsonIterator.parse("""
            {"bearerToken":"t","severityIds":{"FATAL":"sev-9"}}""")));
    assertTrue(jsonEx.getMessage().contains("FATAL"));
  }

  @Test
  void incidentClientMappingDefaultsAbsent() {
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t"}"""));
    assertTrue(config.severityIds().isEmpty());
    assertNull(config.incidentTypeId());
    assertNull(config.statusId());
    assertNull(config.visibility());
    assertNull(config.mode());
    assertNull(config.incidentTimestampId());
  }

  @Test
  void createIncidentClientBuilderSeedsMapping() {
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t","severityIds":{"CRITICAL":"sev-1"},"visibility":"private",
        "incidentTimestampId":"ts-1"}"""));
    final var client = config.createClientBuilder().createClient();
    assertNotNull(config.createIncidentClientBuilder(client).createClient());
    assertEquals("ts-1", config.incidentTimestampId());
    // that the seeded id reaches the serialized request is pinned by
    // IncidentIoIncidentClientTests#configSeedsTheIncidentTimestampId, which lives in the
    // adapter's package where toRequest is reachable

    // visibility is required by the adapter builder, not at config parse time
    final var noVisibility = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t"}"""));
    final var ex = assertThrows(NullPointerException.class,
        () -> noVisibility.createIncidentClientBuilder(client).createClient());
    assertEquals("'visibility' is required.", ex.getMessage());
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
