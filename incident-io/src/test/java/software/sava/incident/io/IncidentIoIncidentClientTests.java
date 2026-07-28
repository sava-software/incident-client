package software.sava.incident.io;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.io.config.IncidentIoConfig;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
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
        {"idempotency_key":"idem-1","name":"title-1","summary":"description-1","incident_type_id":"type-1","mode":"standard","severity_id":"sev-critical","incident_status_id":"status-1","visibility":"public"}""", request.body()
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
  void nullModeIsOmittedAndVisibilityIsRequired() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) IncidentIoIncidentClient.build(stub)
        .severityId(IncidentSeverity.ERROR, "sev-error")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .visibility(CreateIncidentRequest.Visibility.PUBLIC)
        .mode(null)
        .createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("idem-4")
        .summary("title-4")
        .severity(IncidentSeverity.ERROR)
        .create());
    assertNull(request.mode());
    assertEquals("public", request.visibility());

    // the API rejects create requests without a visibility; fail at build time instead.
    // The setter itself accepts null — the requirement is enforced at createClient.
    final var missingVisibility = IncidentIoIncidentClient.build(stub)
        .severityId(IncidentSeverity.ERROR, "sev-error")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .visibility(null);
    final var thrown = assertThrows(NullPointerException.class, missingVisibility::createClient);
    assertEquals("'visibility' is required.", thrown.getMessage());
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

  /// `source` and `customDetails` have no id-free incident.io equivalent, so they are
  /// appended to the incident summary rather than dropped.
  @Test
  void sourceAndCustomDetailsAreAppendedToTheSummary() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("idem-1")
        .summary("Validator missed its leader slot")
        .details("No block produced for slot 350000000.")
        .severity(IncidentSeverity.CRITICAL)
        .source("validator-07.example.com")
        .customDetail("region", "us-east-1")
        .customDetail("slot", 350000000L)
        .customDetail("missed", true)
        .create());

    assertEquals("""
        No block produced for slot 350000000.

        Source: validator-07.example.com
        region: us-east-1
        slot: 350000000
        missed: true""", request.summary()
    );
    // insertion order is preserved, and the whole block is escaped as one JSON string
    assertEquals("""
        {"idempotency_key":"idem-1","name":"Validator missed its leader slot",\
        "summary":"No block produced for slot 350000000.\\n\\nSource: validator-07.example.com\\n\
        region: us-east-1\\nslot: 350000000\\nmissed: true",\
        "incident_type_id":"type-1","mode":"standard","severity_id":"sev-critical",\
        "incident_status_id":"status-1","visibility":"public"}""", request.body()
    );
  }

  /// Each of the three inputs can be absent independently; none may introduce a stray
  /// leading, trailing, or doubled newline.
  @Test
  void summaryAppendixHandlesEachPartBeingAbsent() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    // source only
    assertEquals("Source: host-1", client.toRequest(IncidentAlert.build()
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .source("host-1")
        .create()).summary());

    // one custom detail only — no separator before the first line
    assertEquals("region: us-east-1", client.toRequest(IncidentAlert.build()
        .summary("title-2")
        .severity(IncidentSeverity.CRITICAL)
        .customDetail("region", "us-east-1")
        .create()).summary());

    // details only: unchanged, no trailing blank line
    assertEquals("just details", client.toRequest(IncidentAlert.build()
        .summary("title-3")
        .details("just details")
        .severity(IncidentSeverity.CRITICAL)
        .create()).summary());

    // a blank source is skipped, so the first custom detail leads
    assertEquals("region: us-east-1", client.toRequest(IncidentAlert.build()
        .summary("title-4")
        .severity(IncidentSeverity.CRITICAL)
        .source("  ")
        .customDetail("region", "us-east-1")
        .create()).summary());

    // blank details are treated as absent: no leading empty line
    assertEquals("Source: host-2", client.toRequest(IncidentAlert.build()
        .summary("title-5")
        .details("  ")
        .severity(IncidentSeverity.CRITICAL)
        .source("host-2")
        .create()).summary());

    // nothing at all: the summary stays absent and is omitted from the body
    final var bare = client.toRequest(IncidentAlert.build()
        .summary("title-6")
        .severity(IncidentSeverity.CRITICAL)
        .create());
    assertNull(bare.summary());
    assertFalse(bare.body().contains("\"summary\""), bare.body());
  }

  @Test
  void customDetailValuesRenderIncludingNulls() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub).createClient();

    assertEquals("""
        details

        a: 1
        b: null
        c: text""", client.toRequest(IncidentAlert.build()
        .summary("title-1")
        .details("details")
        .severity(IncidentSeverity.CRITICAL)
        .customDetail("a", 1)
        .customDetail("b", null)
        .customDetail("c", "text")
        .create()).summary()
    );
  }

  /// The alert timestamp needs a workspace-specific incident timestamp id to land on, so
  /// both halves have to be present — each missing half independently drops it.
  @Test
  void alertTimestampNeedsBothAnIdAndATimestamp() {
    final var stub = new StubIoClient();
    final var timestamp = ZonedDateTime.parse("2024-05-01T12:00:00Z");

    final var configured = (IncidentIoIncidentClient) builder(stub)
        .incidentTimestampId("ts-1")
        .createClient();
    final var withBoth = configured.toRequest(IncidentAlert.build()
        .key("idem-1")
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(timestamp)
        .create());
    assertEquals("""
        {"idempotency_key":"idem-1","name":"title-1","incident_type_id":"type-1","mode":"standard",\
        "severity_id":"sev-critical","incident_status_id":"status-1","visibility":"public",\
        "incident_timestamp_values":[{"incident_timestamp_id":"ts-1","value":"2024-05-01T12:00:00Z"}]}""",
        withBoth.body()
    );

    // id configured, alert carries no timestamp
    final var noTimestamp = configured.toRequest(IncidentAlert.build()
        .key("idem-2")
        .summary("title-2")
        .severity(IncidentSeverity.CRITICAL)
        .create());
    assertEquals(List.of(), List.copyOf(noTimestamp.incidentTimestampValues()));

    // timestamp present, no id configured
    final var unconfigured = (IncidentIoIncidentClient) builder(stub).createClient();
    final var noId = unconfigured.toRequest(IncidentAlert.build()
        .key("idem-3")
        .summary("title-3")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(timestamp)
        .create());
    assertEquals(List.of(), List.copyOf(noId.incidentTimestampValues()));

    // neither
    final var neither = unconfigured.toRequest(IncidentAlert.build()
        .key("idem-4")
        .summary("title-4")
        .severity(IncidentSeverity.CRITICAL)
        .create());
    assertEquals(List.of(), List.copyOf(neither.incidentTimestampValues()));
  }

  /// `incident_timestamp_id` is required by the payload, so it serializes even when
  /// blank; the builder normalizes a blank id to unset so a misconfigured id drops the
  /// timestamp instead of producing a request the API can only reject.
  @Test
  void blankIncidentTimestampIdIsTreatedAsUnset() {
    final var stub = new StubIoClient();
    final var alert = IncidentAlert.build()
        .key("idem-1")
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(ZonedDateTime.parse("2024-05-01T12:00:00Z"))
        .create();

    for (final var blank : new String[]{"  ", "", null}) {
      final var client = (IncidentIoIncidentClient) builder(stub)
          .incidentTimestampId(blank)
          .createClient();
      assertEquals(List.of(), List.copyOf(client.toRequest(alert).incidentTimestampValues()),
          "blank id '" + blank + "' should be treated as unset");
    }

    // and a set id is not normalized away
    final var client = (IncidentIoIncidentClient) builder(stub)
        .incidentTimestampId(" ts-1 ")
        .createClient();
    assertEquals(" ts-1 ",
        List.copyOf(client.toRequest(alert).incidentTimestampValues()).getFirst().incidentTimestampId());
  }

  /// A region zone would render an invalid RFC 3339 `[Area/City]` suffix; the adapter
  /// converts to an offset before it reaches the request.
  @Test
  void regionZonedTimestampSerializesAsAnOffset() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) builder(stub)
        .incidentTimestampId("ts-1")
        .createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .key("idem-1")
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(ZonedDateTime.of(
            LocalDateTime.parse("2024-05-01T12:00:00"), ZoneId.of("America/New_York")))
        .create());
    assertTrue(request.body().contains("""
        "value":"2024-05-01T12:00:00-04:00\""""), request.body());
    assertFalse(request.body().contains("America/New_York"), request.body());
  }

  @Test
  void configSeedsTheIncidentTimestampId() {
    final var stub = new StubIoClient();
    final var config = IncidentIoConfig.parseConfig(JsonIterator.parse("""
        {"bearerToken":"t","visibility":"private","incidentTimestampId":"ts-config"}"""));
    final var client = (IncidentIoIncidentClient) config.createIncidentClientBuilder(stub).createClient();

    final var request = client.toRequest(IncidentAlert.build()
        .summary("title-1")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(ZonedDateTime.parse("2024-05-01T12:00:00Z"))
        .create());
    final var timestampValues = List.copyOf(request.incidentTimestampValues());
    assertEquals(1, timestampValues.size());
    assertEquals("ts-config", timestampValues.getFirst().incidentTimestampId());
  }

  @Test
  void severityIdsMap() {
    final var stub = new StubIoClient();
    final var client = (IncidentIoIncidentClient) IncidentIoIncidentClient.build(stub)
        .severityIds(Map.of(IncidentSeverity.WARNING, "sev-warning"))
        .visibility(CreateIncidentRequest.Visibility.PUBLIC)
        .createClient();
    final var request = client.toRequest(IncidentAlert.build()
        .summary("title-5")
        .severity(IncidentSeverity.WARNING)
        .create());
    assertEquals("sev-warning", request.severityId());
  }
}
