package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

final class PagerDutyIncidentClientTests {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2024, 3, 4, 5, 6, 7, 0, UTC);

  private static final class StubEventClient implements PagerDutyEventClient {

    private PagerDutyEventPayload triggeredPayload;
    private String resolvedDedupKey;
    private PagerDutyEventResponse response;

    @Override
    public String defaultClientName() {
      return "stub-client";
    }

    @Override
    public String defaultClientUrl() {
      return null;
    }

    @Override
    public String defaultRoutingKey() {
      return "stub-routing-key";
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> acknowledgeEvent(final String routingKey, final String dedupKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> resolveEvent(final String routingKey, final String dedupKey) {
      this.resolvedDedupKey = dedupKey;
      return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                                  final String clientUrl,
                                                                  final String routingKey,
                                                                  final PagerDutyEventPayload payload) {
      this.triggeredPayload = payload;
      return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> changeEvent(final String routingKey,
                                                                 final PagerDutyChangeEventPayload payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public URI endpoint() {
      return URI.create("https://events.pagerduty.com");
    }

    @Override
    public HttpClient httpClient() {
      return HTTP_CLIENT;
    }
  }

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static PagerDutyEventResponse response(final String dedupKey) {
    final var parser = PagerDutyEventResponse.parser();
    parser.status("success").message("Event processed").dedupKey(dedupKey);
    return parser.create();
  }

  @Test
  void mapSeverity() {
    assertEquals(PagerDutySeverity.critical, PagerDutyIncidentClient.mapSeverity(IncidentSeverity.CRITICAL));
    assertEquals(PagerDutySeverity.error, PagerDutyIncidentClient.mapSeverity(IncidentSeverity.ERROR));
    assertEquals(PagerDutySeverity.warning, PagerDutyIncidentClient.mapSeverity(IncidentSeverity.WARNING));
    assertEquals(PagerDutySeverity.info, PagerDutyIncidentClient.mapSeverity(IncidentSeverity.INFO));
  }

  @Test
  void toPayload() {
    final var payload = PagerDutyIncidentClient.toPayload(IncidentAlert.build()
        .key("dedup-1")
        .summary("summary-1")
        .details("details-1")
        .severity(IncidentSeverity.WARNING)
        .source("host-1")
        .timestamp(TIMESTAMP)
        .customDetail("k1", "v1")
        .customDetail("k2", 2)
        .create());
    assertEquals("dedup-1", payload.dedupKey());
    assertEquals(
        "{\"summary\":\"summary-1\",\"source\":\"host-1\",\"severity\":\"warning\""
            + ",\"timestamp\":\"2024-03-04T05:06:07Z\""
            + ",\"custom_details\":{\"details\":\"details-1\",\"k1\":\"v1\",\"k2\":2}}",
        payload.payloadJson()
    );
  }

  @Test
  void toPayloadDefaults() {
    final var payload = PagerDutyIncidentClient.toPayload(IncidentAlert.build()
        .summary("summary-2")
        .severity(IncidentSeverity.CRITICAL)
        .timestamp(TIMESTAMP)
        .create());
    // no key -> generated dedup key; no source -> fallback; no details -> no custom_details
    assertEquals(36, payload.dedupKey().length());
    assertEquals(
        "{\"summary\":\"summary-2\",\"source\":\"unknown\",\"severity\":\"critical\""
            + ",\"timestamp\":\"2024-03-04T05:06:07Z\"}",
        payload.payloadJson()
    );
  }

  @Test
  void toPayloadBlankSourceAndDetails() {
    final var payload = PagerDutyIncidentClient.toPayload(IncidentAlert.build()
        .key("dedup-b")
        .summary("summary-b")
        .details("  ")
        .severity(IncidentSeverity.ERROR)
        .source("  ")
        .timestamp(TIMESTAMP)
        .create());
    // blank source falls back; blank details are omitted
    assertEquals(
        "{\"summary\":\"summary-b\",\"source\":\"unknown\",\"severity\":\"error\",\"timestamp\":\"2024-03-04T05:06:07Z\"}",
        payload.payloadJson()
    );
  }

  @Test
  void delegatesHttpClientAndDescribesItself() {
    final var stub = new StubEventClient();
    final var client = PagerDutyIncidentClient.createClient(stub);
    assertSame(HTTP_CLIENT, client.httpClient());
    assertTrue(client.toString().startsWith("PagerDutyIncidentClient{"));
  }

  @Test
  void reportAndResolve() {
    final var stub = new StubEventClient();
    final var client = PagerDutyIncidentClient.createClient(stub);
    assertTrue(client.supportsResolve());
    assertEquals(stub.endpoint(), client.endpoint());

    stub.response = response("dedup-3");
    final var reported = client.reportIncident(IncidentAlert.build()
        .key("dedup-3")
        .summary("summary-3")
        .severity(IncidentSeverity.ERROR)
        .timestamp(TIMESTAMP)
        .create()).join();
    assertEquals("dedup-3", reported.key());
    assertEquals("success", reported.status());
    assertNull(reported.url());
    assertEquals("dedup-3", stub.triggeredPayload.dedupKey());

    // a response without a dedup key falls back to the requested one
    stub.response = response(null);
    final var resolved = client.resolveIncident("dedup-3").join();
    assertEquals("dedup-3", resolved.key());
    assertEquals("success", resolved.status());
    assertEquals("dedup-3", stub.resolvedDedupKey);
  }
}
