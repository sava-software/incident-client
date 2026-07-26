package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

final class IncidentClientsTests {

  private static final class StubClient implements IncidentClient {

    @Override
    public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsResolve() {
      return false;
    }

    @Override
    public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URI endpoint() {
      return null;
    }

    @Override
    public HttpClient httpClient() {
      return null;
    }
  }

  private static final class StubFactory implements IncidentClientFactory {

    private final String provider;
    private final IncidentClient client;
    private Properties properties;
    private String prefix;
    private boolean parsedJson;

    private StubFactory(final String provider) {
      this.provider = provider;
      this.client = new StubClient();
    }

    @Override
    public String provider() {
      return provider;
    }

    @Override
    public IncidentClient createClient(final Properties properties, final String prefix) {
      this.properties = properties;
      this.prefix = prefix;
      return client;
    }

    @Override
    public IncidentClient createClient(final JsonIterator ji) {
      this.parsedJson = true;
      ji.skip();
      return client;
    }
  }

  @Test
  void missingProvider() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(new Properties()));
    assertTrue(ex.getMessage().contains("'provider' is required"));
  }

  @Test
  void blankProvider() {
    final var properties = new Properties();
    properties.setProperty("provider", "  ");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties));
    assertTrue(ex.getMessage().contains("'provider' is required"));
  }

  @Test
  void loadFactoryMatchesIgnoringCaseAndSeparators() {
    final var alpha = new StubFactory("alpha");
    final var incidentIo = new StubFactory("incident.io");
    final var factories = List.<IncidentClientFactory>of(alpha, incidentIo);

    assertSame(alpha, IncidentClients.loadFactory("alpha", factories));
    assertSame(alpha, IncidentClients.loadFactory("ALPHA", factories));
    assertSame(incidentIo, IncidentClients.loadFactory("incident-io", factories));
    assertSame(incidentIo, IncidentClients.loadFactory("IncidentIO", factories));
  }

  @Test
  void loadFactoryDoesNotMatchTheWrongFactory() {
    final var alpha = new StubFactory("alpha");
    final var beta = new StubFactory("beta");
    // beta must resolve past the non-matching first factory
    assertSame(beta, IncidentClients.loadFactory("beta", List.of(alpha, beta)));
  }

  @Test
  void loadFactoryUnknownListsAvailable() {
    final var factories = List.<IncidentClientFactory>of(new StubFactory("alpha"), new StubFactory("beta"));
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.loadFactory("nonesuch", factories));
    assertTrue(ex.getMessage().contains("'nonesuch'"));
    assertTrue(ex.getMessage().contains("alpha"));
    assertTrue(ex.getMessage().contains("beta"));
  }

  @Test
  void propertiesDispatchPassesOriginalPrefixAndProperties() {
    final var stub = new StubFactory("stub");
    final var properties = new Properties();
    properties.setProperty("svc.provider", "stub");

    final var client = IncidentClients.createClient(properties, "svc", List.of(stub));
    assertSame(stub.client, client);
    assertSame(properties, stub.properties);
    assertEquals("svc", stub.prefix);

    // a dot-suffixed prefix resolves the same provider key and passes through verbatim
    assertSame(stub.client, IncidentClients.createClient(properties, "svc.", List.of(stub)));
    assertEquals("svc.", stub.prefix);
  }

  @Test
  void propertiesDispatchWithoutPrefix() {
    final var stub = new StubFactory("stub");
    final var properties = new Properties();
    properties.setProperty("provider", "stub");

    assertSame(stub.client, IncidentClients.createClient(properties, null, List.of(stub)));
    assertNull(stub.prefix);

    // blank prefix behaves as no prefix
    assertSame(stub.client, IncidentClients.createClient(properties, "  ", List.of(stub)));
    assertEquals("  ", stub.prefix);
  }

  @Test
  void factoryDefaultCreateClientUsesNoPrefix() {
    final var stub = new StubFactory("stub");
    final var properties = new Properties();
    assertSame(stub.client, stub.createClient(properties));
    assertSame(properties, stub.properties);
    assertNull(stub.prefix);
  }

  @Test
  void jsonDispatchHappyPath() {
    final var stub = new StubFactory("stub");
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"stub","config":{"a":1}}"""), List.of(stub));
    assertSame(stub.client, client);
    assertTrue(stub.parsedJson);
  }

  @Test
  void prefixedProviderLookup() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "nonesuch");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties, "incident"));
    assertTrue(ex.getMessage().contains("'nonesuch'"));

    // a dot-suffixed prefix resolves the same key
    final var dotEx = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(properties, "incident."));
    assertTrue(dotEx.getMessage().contains("'nonesuch'"));
  }

  @Test
  void unknownProvider() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.loadFactory("nonesuch"));
    assertTrue(ex.getMessage().contains("No IncidentClientFactory found for provider 'nonesuch'"));
  }

  @Test
  void jsonProviderMustPrecedeConfig() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(JsonIterator.parse("""
            {"config":{},"provider":"nonesuch"}""")));
    assertTrue(ex.getMessage().contains("'provider' must precede 'config'"));
  }

  @Test
  void jsonMissingConfig() {
    final var stub = new StubFactory("stub");
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(JsonIterator.parse("""
            {"provider":"stub"}"""), List.of(stub)));
    assertTrue(ex.getMessage().contains("'config' object is required"));
  }

  @Test
  void jsonUnknownField() {
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(JsonIterator.parse("""
            {"unknown":"value"}""")));
    assertTrue(ex.getMessage().contains("Unknown IncidentClients config field unknown"));
  }
}
