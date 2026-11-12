package software.sava.incident.io.alert;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the alert-event client builder without a wire: the default endpoint and its path
/// encoding, an explicit endpoint, the id requirement, and the token decorator.
final class IncidentIoAlertEventClientTests {

  @Test
  void theDefaultEndpointEncodesTheIdAsOnePathSegment() {
    final var client = IncidentIoAlertEventClient.clientBuilder()
        .alertSourceConfigId("a b/c+dé")
        .token("t")
        .createClient();
    assertEquals("/v2/alert_events/http/a%20b%2Fc%2Bd%C3%A9", client.endpoint().getRawPath());
    assertEquals("https://api.incident.io/v2/alert_events/http/a%20b%2Fc%2Bd%C3%A9", client.endpoint().toString());
  }

  @Test
  void pathSegmentEncoding() {
    // every unreserved character passes through
    final var unreserved = "ABCXYZabcxyz0189-._~";
    assertEquals(unreserved, IncidentIoAlertEventClientImpl.encodePathSegment(unreserved));
    // the characters just outside each unreserved range are encoded
    assertEquals("%40%5B%60%7B%2F%3A%2C%2B",
        IncidentIoAlertEventClientImpl.encodePathSegment("@[`{/:,+"));
    // the neighbours of '_' and '~', reserved and other characters
    assertEquals("%5E%7D%20%25%3F%23%21%2A%27", IncidentIoAlertEventClientImpl.encodePathSegment("^} %?#!*'"));
    // multi-byte UTF-8, uppercase hex, and a surrogate pair's four bytes
    assertEquals("%C3%BF%E2%98%83%F0%9F%98%80", IncidentIoAlertEventClientImpl.encodePathSegment("ÿ☃😀"));
    // control characters and the highest single byte
    assertEquals("%00%0A%7F", IncidentIoAlertEventClientImpl.encodePathSegment("\u0000\n\u007f"));
    assertEquals("", IncidentIoAlertEventClientImpl.encodePathSegment(""));
  }

  @Test
  void anExplicitEndpointIsUsedAsGivenAndTheIdIgnored() {
    final var endpoint = URI.create("https://proxy.example.com/custom/path?token=x");
    final var withId = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(endpoint)
        .alertSourceConfigId("ignored-id")
        .createClient();
    assertEquals(endpoint, withId.endpoint());

    final var withoutId = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(endpoint)
        .createClient();
    assertEquals(endpoint, withoutId.endpoint());

    final var blankId = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(endpoint)
        .alertSourceConfigId(" ")
        .createClient();
    assertEquals(endpoint, blankId.endpoint());
  }

  @Test
  void anIdIsRequiredWithoutAnEndpoint() {
    final var noId = IncidentIoAlertEventClient.clientBuilder();
    assertEquals("'alertSourceConfigId' is required.",
        assertThrows(NullPointerException.class, noId::createClient).getMessage());

    final var blankId = IncidentIoAlertEventClient.clientBuilder().alertSourceConfigId(" \t");
    assertEquals("'alertSourceConfigId' is required.",
        assertThrows(IllegalArgumentException.class, blankId::createClient).getMessage());
  }

  @Test
  void createClientDoesNotFixTheEndpointOnTheBuilder() {
    final var builder = IncidentIoAlertEventClient.clientBuilder().alertSourceConfigId("first");
    assertEquals("/v2/alert_events/http/first", builder.createClient().endpoint().getRawPath());
    assertNull(builder.endpoint());
    assertEquals("first", builder.alertSourceConfigId());
    builder.alertSourceConfigId("second");
    assertEquals("/v2/alert_events/http/second", builder.createClient().endpoint().getRawPath());
  }

  @Test
  void theTokenBecomesABearerAuthorizationHeader() {
    final var builder = IncidentIoAlertEventClient.clientBuilder().token("secret-1");
    final var request = builder.extendRequest()
        .apply(HttpRequest.newBuilder(URI.create("https://example.com/")))
        .build();
    assertEquals(List.of("Bearer secret-1"), request.headers().allValues("Authorization"));
    assertEquals(List.of("application/json"), request.headers().allValues("Content-Type"));
  }

  @Test
  void theSuppliedHttpClientIsUsedAndOneIsCreatedOtherwise() {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var client = IncidentIoAlertEventClient.clientBuilder()
          .alertSourceConfigId("id")
          .httpClient(httpClient)
          .createClient();
      assertSame(httpClient, client.httpClient());
    }
    assertNotNull(IncidentIoAlertEventClient.clientBuilder().alertSourceConfigId("id").createClient().httpClient());
  }

  @Test
  void aNullEventIsRefusedBeforeAnythingIsSent() {
    final var client = IncidentIoAlertEventClient.clientBuilder()
        .endpoint("http://127.0.0.1:9/unreachable")
        .createClient();
    assertEquals("'event' is required.",
        assertThrows(NullPointerException.class, () -> client.sendAlertEvent(null)).getMessage());
  }

  @Test
  void incidentClientBuilderAdaptsThisClient() {
    final var client = IncidentIoAlertEventClient.clientBuilder()
        .alertSourceConfigId("src-1")
        .createClient();
    final var incidentClient = client.incidentClientBuilder().createClient();
    assertInstanceOf(IncidentIoAlertIncidentClient.class, incidentClient);
    assertSame(client.endpoint(), incidentClient.endpoint());
    assertSame(client.httpClient(), incidentClient.httpClient());
  }
}
