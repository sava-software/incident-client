package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the event-client builder's own accessors and its request-decorator composition.
/// The wire tests drive a fully-configured client; these cover the branches that choose
/// *which* decorator gets installed, and the accessors nothing else reads back.
final class PagerDutyEventClientBuilderTests {

  private static HttpRequest.Builder decorate(final PagerDutyEventClient.Builder builder) {
    final var client = builder.createClient();
    assertNotNull(client);
    final var request = HttpRequest.newBuilder(URI.create("https://events.pagerduty.com/v2/enqueue"));
    // createClient installs the decorator on the builder itself
    final var extend = builder.extendRequest();
    assertNotNull(extend, "createClient must install a request decorator when none was supplied");
    return extend.apply(request);
  }

  private static String header(final HttpRequest.Builder builder, final String name) {
    return builder.build().headers().firstValue(name).orElse(null);
  }

  @Test
  void accessorsReadBackTheConfiguredDefaults() {
    final var builder = PagerDutyEventClient.clientBuilder()
        .defaultClientName("svc")
        .defaultClientUrl("https://svc.example.com")
        .defaultRoutingKey("rk")
        .authToken("tok");

    assertEquals("svc", builder.defaultClientName());
    assertEquals("https://svc.example.com", builder.defaultClientUrl());
    assertEquals("rk", builder.defaultRoutingKey());
    assertEquals("tok", builder.authToken());
  }

  @Test
  void defaultsFillTheEndpointWhenUnset() {
    final var client = PagerDutyEventClient.clientBuilder()
        .defaultRoutingKey("rk")
        .createClient();
    assertEquals(URI.create("https://events.pagerduty.com"), client.endpoint());
    assertNotNull(client.httpClient());
  }

  @Test
  void anExplicitEndpointIsNotOverwritten() {
    final var endpoint = URI.create("https://events.eu.pagerduty.com");
    final var client = PagerDutyEventClient.clientBuilder()
        .endpoint(endpoint)
        .defaultRoutingKey("rk")
        .createClient();
    assertEquals(endpoint, client.endpoint());
  }

  @Test
  void withoutAnAuthTokenOnlyContentTypeIsSet() {
    final var decorated = decorate(PagerDutyEventClient.clientBuilder().defaultRoutingKey("rk"));
    assertEquals("application/json", header(decorated, "Content-Type"));
    assertNull(header(decorated, "Authorization"),
        "no token means no Authorization header, not an empty one");
  }

  @Test
  void aBlankAuthTokenIsTreatedAsAbsent() {
    final var decorated = decorate(PagerDutyEventClient.clientBuilder()
        .defaultRoutingKey("rk")
        .authToken("   "));
    assertEquals("application/json", header(decorated, "Content-Type"));
    assertNull(header(decorated, "Authorization"));
  }

  @Test
  void anAuthTokenBecomesATokenHeaderAlongsideContentType() {
    final var decorated = decorate(PagerDutyEventClient.clientBuilder()
        .defaultRoutingKey("rk")
        .authToken("abc123"));
    assertEquals("Token token=abc123", header(decorated, "Authorization"));
    assertEquals("application/json", header(decorated, "Content-Type"));
  }

  @Test
  void acallerSuppliedDecoratorIsLeftAlone() {
    // an explicit extendRequest wins: createClient must not replace it with either
    // header-setting variant, even when an authToken is also present
    final var builder = PagerDutyEventClient.clientBuilder()
        .defaultRoutingKey("rk")
        .authToken("abc123")
        .extendRequest(request -> request.setHeader("X-Custom", "kept"));
    final var client = builder.createClient();
    assertNotNull(client);

    final var decorated = builder.extendRequest()
        .apply(HttpRequest.newBuilder(URI.create("https://events.pagerduty.com/v2/enqueue")));
    assertEquals("kept", header(decorated, "X-Custom"));
    assertNull(header(decorated, "Authorization"));
    assertNull(header(decorated, "Content-Type"));
  }
}
