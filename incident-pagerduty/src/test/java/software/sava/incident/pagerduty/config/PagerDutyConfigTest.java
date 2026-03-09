package software.sava.incident.pagerduty.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyConfigTest {

  @Test
  void parseFullConfig() {
    final var json = """
        {"routingKey":"test-routing-key","authToken":"test-auth-token"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertEquals("test-auth-token", config.authToken());
  }

  @Test
  void parseConfigWithoutAuthToken() {
    final var json = """
        {"routingKey":"test-routing-key"}""";
    final var config = PagerDutyConfig.parseConfig(JsonIterator.parse(json));
    assertEquals("test-routing-key", config.routingKey());
    assertNull(config.authToken());
  }

  @Test
  void parseConfigMissingRoutingKey() {
    final var json = """
        {"authToken":"test-auth-token"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json)));
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void parseConfigBlankRoutingKey() {
    final var json = """
        {"routingKey":"  ","authToken":"test-auth-token"}""";
    final var ex = assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json)));
    assertTrue(ex.getMessage().contains("routingKey"));
  }

  @Test
  void parseConfigUnknownField() {
    final var json = """
        {"routingKey":"test-routing-key","unknown":"value"}""";
    assertThrows(IllegalStateException.class,
        () -> PagerDutyConfig.parseConfig(JsonIterator.parse(json)));
  }
}
