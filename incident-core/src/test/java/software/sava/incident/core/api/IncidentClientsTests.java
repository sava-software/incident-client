package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

// no provider modules are on the test path, so only the error paths are observable here;
// dispatch to a live factory is covered by the provider modules' tests.
final class IncidentClientsTests {

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
    final var ex = assertThrows(IllegalStateException.class,
        () -> IncidentClients.createClient(JsonIterator.parse("""
            {"provider":"nonesuch"}""")));
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
