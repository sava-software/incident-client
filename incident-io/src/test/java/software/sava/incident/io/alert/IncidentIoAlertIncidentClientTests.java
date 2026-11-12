package software.sava.incident.io.alert;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentService;
import software.sava.incident.core.api.IncidentSeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the provider-neutral alert-source adapter: the firing event a report sends, the
/// resolved event a resolve sends, and the response mapping.
final class IncidentIoAlertIncidentClientTests {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final URI ENDPOINT = URI.create("https://api.incident.io/v2/alert_events/http/stub-source");

  private record StubResponse(String status, String message, String deduplicationKey) implements AlertEventResponse {
  }

  private static final class StubClient implements IncidentIoAlertEventClient {

    private final List<AlertEvent> events = new ArrayList<>();
    private AlertEventResponse response = new StubResponse("stub-status", "stub-message", null);

    @Override
    public CompletableFuture<AlertEventResponse> sendAlertEvent(final AlertEvent event) {
      events.add(event);
      return CompletableFuture.completedFuture(response);
    }

    @Override
    public URI endpoint() {
      return ENDPOINT;
    }

    @Override
    public HttpClient httpClient() {
      return HTTP_CLIENT;
    }

    @Override
    public String toString() {
      return "StubClient";
    }
  }

  private static IncidentAlert.Builder alert() {
    return IncidentAlert.build()
        .summary("Disk full")
        .severity(IncidentSeverity.WARNING);
  }

  @Test
  void aReportSendsEveryAlertField() {
    final var stub = new StubClient();
    final var client = stub.incidentClientBuilder().createClient();
    final var timestamp = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 123_000_000, ZoneId.of("Europe/Paris"));

    final var response = client.reportIncident(alert()
        .key("alert-key-1")
        .details("**db-1** is at 99%\nsecond line")
        .severity(IncidentSeverity.CRITICAL)
        .source("db-1.example.com")
        .timestamp(timestamp)
        .customDetail("region", "eu-west-1")
        .customDetail("count", 3)
        .create()).join();

    assertEquals(1, stub.events.size());
    final var event = stub.events.getFirst();
    // the region zone renders as its offset: RFC 3339 has no [Area/City] suffix
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"alert-key-1",\
        "description":"**db-1** is at 99%\\nsecond line","metadata":{"severity":"critical",\
        "source":"db-1.example.com","timestamp":"2026-01-02T03:04:05.123+01:00",\
        "region":"eu-west-1","count":3}}""", event.body());
    assertEquals("alert-key-1", response.key());
    assertEquals("stub-status", response.status());
    assertNull(response.url());
  }

  @Test
  void aMinimalAlertGetsAGeneratedKeyThatTheResponseReturns() {
    final var stub = new StubClient();
    final var client = stub.incidentClientBuilder().createClient();

    final var response = client.reportIncident(alert().create()).join();

    final var event = stub.events.getFirst();
    final var key = event.deduplicationKey();
    assertEquals(UUID.fromString(key).toString(), key, "a generated key is a UUID");
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"%s","metadata":{"severity":"warning"}}"""
        .formatted(key), event.body());
    // the response carries no key, so the key that was sent is returned
    assertEquals(key, response.key());

    final var blankKey = client.reportIncident(alert().key(" \t").create()).join();
    final var blankKeyEvent = stub.events.getLast();
    assertEquals(UUID.fromString(blankKeyEvent.deduplicationKey()).toString(), blankKeyEvent.deduplicationKey());
    assertEquals(blankKeyEvent.deduplicationKey(), blankKey.key());
  }

  @Test
  void theResponseKeyWinsWhenTheProviderReturnsOne() {
    final var stub = new StubClient();
    stub.response = new StubResponse("success", "accepted", "provider-key");
    final var client = stub.incidentClientBuilder().createClient();

    final var response = client.reportIncident(alert().key("sent-key").create()).join();
    assertEquals("provider-key", response.key());
    assertEquals("success", response.status());
    assertNull(response.url());
  }

  @Test
  void everySeverityIsSentInLowerCase() {
    for (final var severity : IncidentSeverity.values()) {
      final var event = IncidentIoAlertIncidentClient.toEvent(alert().key("k").severity(severity).create());
      assertEquals("""
          {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{"severity":"%s"}}"""
          .formatted(severity.name().toLowerCase(java.util.Locale.ROOT)), event.body());
    }
  }

  @Test
  void aBlankSourceAndANullTimestampAreLeftOut() {
    final var event = IncidentIoAlertIncidentClient.toEvent(alert().key("k").source(" ").details(" ").create());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{"severity":"warning"}}""",
        event.body());
  }

  @Test
  void aCustomDetailNamedLikeABuiltInReplacesItInPlace() {
    final var event = IncidentIoAlertIncidentClient.toEvent(alert()
        .key("k")
        .source("db-1")
        .timestamp(ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneId.of("UTC")))
        .customDetail("first", "f")
        .customDetail("timestamp", "custom-time")
        .customDetail("severity", "custom-severity")
        .customDetail("source", "custom-source")
        .create());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{\
        "severity":"custom-severity","source":"custom-source","timestamp":"custom-time","first":"f"}}""",
        event.body());
  }

  @Test
  void aCustomDetailNamedSourceWithoutAnAlertSourceKeepsItsOwnPosition() {
    final var event = IncidentIoAlertIncidentClient.toEvent(alert()
        .key("k")
        .customDetail("first", "f")
        .customDetail("source", "custom-source")
        .create());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{\
        "severity":"warning","first":"f","source":"custom-source"}}""", event.body());
  }

  @Test
  void customDetailsTheEventWouldRefuseAreSentAsText() {
    final var array = new int[]{1, 2};
    final var event = IncidentIoAlertIncidentClient.toEvent(alert()
        .key("k")
        .customDetail("map", Map.of("a", 1))
        .customDetail("nested", List.of(List.of(1, 2)))
        .customDetail("nan", Double.NaN)
        .customDetail("infinite", Float.POSITIVE_INFINITY)
        .customDetail("array", array)
        .customDetail("list", List.of(1, "two"))
        .customDetail("number", 2.5d)
        .create());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{"severity":"warning",\
        "map":"{a=1}","nested":"[[1, 2]]","nan":"NaN","infinite":"Infinity","array":"%s",\
        "list":[1,"two"],"number":2.5}}""".formatted(String.valueOf(array)), event.body());
  }

  @Test
  void aNullCustomDetailKeyIsSkippedAndANullValueSent() {
    final var event = IncidentIoAlertIncidentClient.toEvent(alert()
        .key("k")
        .customDetail("first", "f")
        .customDetail(null, "dropped")
        .customDetail("empty", null)
        .create());
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"k","metadata":{\
        "severity":"warning","first":"f","empty":null}}""", event.body());
  }

  @Test
  void aResolveSendsTheKeyAsTitleByDefault() {
    final var stub = new StubClient();
    final var client = stub.incidentClientBuilder().createClient();

    final var response = client.resolveIncident("alert-key-1").join();

    assertEquals("""
        {"title":"alert-key-1","status":"resolved","deduplication_key":"alert-key-1"}""",
        stub.events.getFirst().body());
    assertEquals("alert-key-1", response.key());
    assertEquals("stub-status", response.status());
    assertNull(response.url());
  }

  @Test
  void aResolveSendsTheConfiguredResolveTitle() {
    final var stub = new StubClient();
    stub.response = new StubResponse("success", "accepted", "provider-key");
    final var builder = stub.incidentClientBuilder().resolveTitle("Recovered: disk space");
    assertEquals("Recovered: disk space", builder.resolveTitle());

    final var response = builder.createClient().resolveIncident("alert-key-1").join();

    assertEquals("""
        {"title":"Recovered: disk space","status":"resolved","deduplication_key":"alert-key-1"}""",
        stub.events.getFirst().body());
    assertEquals("provider-key", response.key());
    assertEquals("success", response.status());
  }

  @Test
  void aBlankResolveTitleCountsAsNotConfigured() {
    for (final var title : new String[]{null, "", " \t"}) {
      final var stub = new StubClient();
      final var builder = stub.incidentClientBuilder().resolveTitle(title);
      assertNull(builder.resolveTitle());
      builder.createClient().resolveIncident("k-2").join();
      assertEquals("""
          {"title":"k-2","status":"resolved","deduplication_key":"k-2"}""", stub.events.getFirst().body());
    }
  }

  @Test
  void aNullOrBlankResolveKeyThrowsAtOnceWithNothingSent() {
    final var stub = new StubClient();
    final var client = stub.incidentClientBuilder().resolveTitle("Recovered").createClient();

    final var nullKey = assertThrows(NullPointerException.class, () -> client.resolveIncident(null));
    assertEquals("A resolve requires the alert's key.", nullKey.getMessage());
    assertTrue(stub.events.isEmpty());

    for (final var key : new String[]{"", " \t"}) {
      final var blankKey = assertThrows(IllegalArgumentException.class, () -> client.resolveIncident(key));
      assertEquals("A resolve requires the alert's key.", blankKey.getMessage());
      assertTrue(stub.events.isEmpty());
    }
  }

  /// A blank key is a caller error: through the service it must not be retried as a transport
  /// failure, which on the unbounded overload would retry forever and send nothing.
  @Test
  void aBlankResolveKeyIsNotRetriedByTheService() {
    final var stub = new StubClient();
    final var service = IncidentService.createService(stub.incidentClientBuilder().createClient());

    for (final var key : new String[]{"", " \t"}) {
      final var thrown = assertThrows(IllegalArgumentException.class,
          () -> service.resolveIncident(key, 0L, 0L, TimeUnit.MILLISECONDS));
      assertEquals("A resolve requires the alert's key.", thrown.getMessage());
    }
    assertTrue(stub.events.isEmpty());

    // a null key never reaches the adapter: the service returns null for it
    assertNull(service.resolveIncident((String) null, 0L, 0L, TimeUnit.MILLISECONDS));
    assertTrue(stub.events.isEmpty());
  }

  @Test
  void capabilitiesAndDelegation() {
    final var stub = new StubClient();
    final var client = IncidentIoAlertIncidentClient.build(stub).createClient();
    assertInstanceOf(IncidentIoAlertIncidentClient.class, client);
    assertTrue(client.supportsResolve());
    assertEquals(0, client.maxKeyLength());
    assertSame(ENDPOINT, client.endpoint());
    assertSame(HTTP_CLIENT, client.httpClient());
    assertEquals("IncidentIoAlertIncidentClient{client=StubClient}", client.toString());
  }

  @Test
  void aClientIsRequired() {
    assertEquals("'client' is required.",
        assertThrows(NullPointerException.class, () -> IncidentIoAlertIncidentClient.build(null)).getMessage());
  }
}
