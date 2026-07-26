package software.sava.incident.io;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClients;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

final class IncidentIoIncidentClientFactoryTests {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final class StubIoClient implements IncidentIoClient {

    @Override
    public CompletableFuture<CreateIncidentResponse> createIncident(final CreateIncidentRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URI endpoint() {
      return URI.create("https://api.incident.io/v2/incidents");
    }

    @Override
    public HttpClient httpClient() {
      return HTTP_CLIENT;
    }
  }

  @Test
  void incidentClientBuilder() {
    final var stub = new StubIoClient();
    final var client = stub.incidentClientBuilder()
        .severityId(IncidentSeverity.CRITICAL, "sev-critical")
        .visibility(CreateIncidentRequest.Visibility.PRIVATE)
        .createClient();
    assertInstanceOf(IncidentIoIncidentClient.class, client);
    assertFalse(client.supportsResolve());
    assertSame(stub.httpClient(), client.httpClient());
  }

  @Test
  void createFromProperties() {
    final var properties = new Properties();
    properties.setProperty("incident.provider", "incident.io");
    properties.setProperty("incident.bearerToken", "token-1");
    properties.setProperty("incident.visibility", "private");
    properties.setProperty("incident.severityIds.CRITICAL", "sev-critical");
    properties.setProperty("incident.incidentTypeId", "type-1");
    final var client = IncidentClients.createClient(properties, "incident");
    assertInstanceOf(IncidentIoIncidentClient.class, client);
    assertEquals(URI.create("https://api.incident.io/v2/incidents"), client.endpoint());

    final var request = ((IncidentIoIncidentClient) client).toRequest(IncidentAlert.build()
        .key("idem-1")
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .create());
    assertEquals("sev-critical", request.severityId());
    assertEquals("type-1", request.incidentTypeId());
    assertEquals("private", request.visibility());
  }

  @Test
  void providerMatchIgnoresCaseAndSeparators() {
    final var properties = new Properties();
    properties.setProperty("provider", "IncidentIO");
    properties.setProperty("bearerToken", "token-1");
    properties.setProperty("visibility", "public");
    assertInstanceOf(IncidentIoIncidentClient.class, IncidentClients.createClient(properties));
  }

  @Test
  void createFromJson() {
    final var client = IncidentClients.createClient(JsonIterator.parse("""
        {"provider":"incident-io","config":{"bearerToken":"token-1","visibility":"public",
        "severityIds":{"ERROR":"sev-error"},"statusId":"status-1","mode":"standard"}}"""));
    assertInstanceOf(IncidentIoIncidentClient.class, client);

    final var request = ((IncidentIoIncidentClient) client).toRequest(IncidentAlert.build()
        .key("idem-2")
        .summary("title-2")
        .severity(IncidentSeverity.ERROR)
        .create());
    assertEquals("sev-error", request.severityId());
    assertEquals("status-1", request.statusId());
    assertEquals(CreateIncidentRequest.Mode.standard.name(), request.mode());
    assertEquals("public", request.visibility());
  }

  @Test
  void missingVisibilityFailsAtCreate() {
    final var properties = new Properties();
    properties.setProperty("provider", "incident.io");
    properties.setProperty("bearerToken", "token-1");
    final var ex = assertThrows(NullPointerException.class,
        () -> IncidentClients.createClient(properties));
    assertEquals("'visibility' is required.", ex.getMessage());
  }
}
