package software.sava.incident.io;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

final class IncidentIoIncidentClientTests {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static final class StubIoClient implements IncidentIoClient {

    private CreateIncidentRequest request;
    private CreateIncidentResponse response;

    @Override
    public CompletableFuture<CreateIncidentResponse> createIncident(final CreateIncidentRequest request) {
      this.request = request;
      return CompletableFuture.completedFuture(response);
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

  private static IncidentIoIncidentClient.Builder builder(final StubIoClient stub) {
    return IncidentIoIncidentClient.build(stub)
        .severityId(IncidentSeverity.CRITICAL, "sev-critical")
        .severityId(IncidentSeverity.ERROR, "sev-error")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .visibility(CreateIncidentRequest.Visibility.PUBLIC)
        .mode(CreateIncidentRequest.Mode.standard);
  }

  @Test
  void toRequest() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("idem-1")
        .summary("title-1")
        .details("description-1")
        .severity(IncidentSeverity.CRITICAL)
        .create());
    assertEquals("""
        {"idempotency_key":"idem-1","name":"title-1","summary":"description-1","incident_type_id":"type-1","mode":"standard","severity_id":"sev-critical","status_id":"status-1","visibility":"public"}""", request.body()
    );
  }

  @Test
  void blankAlertKeyGetsGeneratedIdempotencyKey() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("  ")
        .summary("title-3")
        .severity(IncidentSeverity.ERROR)
        .create());
    assertDoesNotThrow(() -> java.util.UUID.fromString(request.idempotencyKey()));
  }

  @Test
  void nullEnumBuilderValuesAreOmitted() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) IncidentIoIncidentClient.build(stub)
        .severityId(IncidentSeverity.ERROR, "sev-error")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .visibility(null)
        .mode(null)
        .createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("idem-4")
        .summary("title-4")
        .severity(IncidentSeverity.ERROR)
        .create());
    assertNull(request.visibility());
    assertNull(request.mode());
  }

  @Test
  void delegatesHttpClientAndDescribesItself() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();
    assertSame(stub.httpClient(), client.httpClient());
    assertTrue(client.toString().startsWith("IncidentIoIncidentClient{"));
  }

  @Test
  void missingKeyAndUnmappedSeverity() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .summary("title-2")
        .severity(IncidentSeverity.INFO)
        .create());
    // no key -> generated idempotency key; INFO is not mapped -> severity omitted
    assertEquals(36, request.idempotencyKey().length());
    assertNull(request.severityId());
  }

  @Test
  void reportIncidentMapsResponse() {
    final var stub = new StubIoClient();
    stub.response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"id":"inc-1","permalink":"https://app.incident.io/incidents/1","reference":"INC-1",
        "incident_status":{"id":"status-1","name":"Investigating"}}}"""));
    final var client = builder(stub).createClient();
    assertFalse(client.supportsResolve());
    assertEquals(stub.endpoint(), client.endpoint());

    final var response = client.reportIncident(IncidentAlert.build()
        .summary("title-3")
        .severity(IncidentSeverity.ERROR)
        .create()).join();
    assertEquals("inc-1", response.key());
    assertEquals("Investigating", response.status());
    assertEquals("https://app.incident.io/incidents/1", response.url());
    assertEquals("sev-error", stub.request.severityId());
  }

  @Test
  void reportIncidentFallsBackToReference() {
    final var stub = new StubIoClient();
    stub.response = CreateIncidentResponseRecord.parse(JsonIterator.parse("""
        {"incident":{"id":"inc-2","reference":"INC-2"}}"""));
    final var response = builder(stub).createClient().reportIncident(IncidentAlert.build()
        .summary("title-4")
        .severity(IncidentSeverity.ERROR)
        .create()).join();
    assertEquals("inc-2", response.key());
    assertEquals("INC-2", response.status());
    assertNull(response.url());
  }

  @Test
  void resolveIsUnsupported() {
    final var client = builder(new StubIoClient()).createClient();
    final var thrown = assertThrows(CompletionException.class, () -> client.resolveIncident("inc-1").join());
    assertInstanceOf(UnsupportedOperationException.class, thrown.getCause());
  }

  @Test
  void severityIdsMap() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) IncidentIoIncidentClient.build(stub)
        .severityIds(Map.of(IncidentSeverity.WARNING, "sev-warning"))
        .createClient();
    final var request = client.toRequest(IncidentAlert.build()
        .summary("title-5")
        .severity(IncidentSeverity.WARNING)
        .create());
    assertEquals("sev-warning", request.severityId());
  }
}
