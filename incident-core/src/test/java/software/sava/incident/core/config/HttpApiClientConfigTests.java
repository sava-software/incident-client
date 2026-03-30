package software.sava.incident.core.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

final class HttpApiClientConfigTests {

  private static final class TestConfig extends HttpApiClientConfig {

    private TestConfig(final URI endpoint, final Duration requestTimeout) {
      super(endpoint, requestTimeout);
    }

    private static TestConfig parseConfig(final JsonIterator ji) {
      final var parser = new TestParser(null);
      ji.testObject(parser);
      return parser.createConfig();
    }

    private static TestConfig parseConfig(final Properties properties) {
      return parseConfig(properties, null);
    }

    private static TestConfig parseConfig(final Properties properties, final String prefix) {
      final var parser = new TestParser(prefix);
      parser.parseConfig(properties);
      return parser.createConfig();
    }
  }

  private static final class TestParser extends HttpApiClientConfig.Parser {

    private TestParser(final String prefix) {
      super(prefix);
    }

    private TestConfig createConfig() {
      return new TestConfig(endpoint, requestTimeout);
    }
  }

  @Test
  void parseJsonFullConfig() {
    final var json = """
        {"endpoint":"https://api.example.com","requestTimeout":"PT5S"}""";
    final var config = TestConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }

  @Test
  void parseJsonWithoutRequestTimeout() {
    final var json = """
        {"endpoint":"https://api.example.com"}""";
    final var config = TestConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertNull(config.requestTimeout());
  }

  @Test
  void parseJsonUnknownField() {
    final var json = """
        {"endpoint":"https://api.example.com","unknown":"value"}""";
    assertThrows(IllegalStateException.class,
        () -> TestConfig.parseConfig(JsonIterator.parse(json)));
  }

  @Test
  void parseJsonRequestTimeoutWithoutPrefix() {
    final var json = """
        {"endpoint":"https://api.example.com","requestTimeout":"5S"}""";
    final var config = TestConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }

  @Test
  void parsePropertiesFullConfig() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://api.example.com");
    properties.setProperty("requestTimeout", "PT5S");
    final var config = TestConfig.parseConfig(properties);
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }

  @Test
  void parsePropertiesWithoutRequestTimeout() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://api.example.com");
    final var config = TestConfig.parseConfig(properties);
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertNull(config.requestTimeout());
  }

  @Test
  void parsePropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("api.endpoint", "https://api.example.com");
    properties.setProperty("api.requestTimeout", "PT5S");
    final var config = TestConfig.parseConfig(properties, "api");
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }

  @Test
  void parsePropertiesWithDotSuffixedPrefix() {
    final var properties = new Properties();
    properties.setProperty("api.endpoint", "https://api.example.com");
    properties.setProperty("api.requestTimeout", "PT5S");
    final var config = TestConfig.parseConfig(properties, "api.");
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }

  @Test
  void parsePropertiesWithBlankPrefix() {
    final var properties = new Properties();
    properties.setProperty("endpoint", "https://api.example.com");
    properties.setProperty("requestTimeout", "PT5S");
    final var config = TestConfig.parseConfig(properties, "  ");
    assertEquals(URI.create("https://api.example.com"), config.endpoint());
    assertEquals(Duration.ofSeconds(5), config.requestTimeout());
  }
}
