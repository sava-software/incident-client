package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

/// Pins what the event client does with a payload builder handed to it in place of a created
/// payload. Property: the client sends what the builder's own `create()` yields, so the
/// builder's required-field rules hold on the wire, and it creates the caller's builder
/// itself, so every hand-off of one builder sends one event. Oracle: hand-written request
/// bodies captured by a `jdk.httpserver`, and the exception `create()` documents for each
/// missing field.
final class PagerDutyEventClientHandoffTests {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2019, 9, 2, 3, 4, 5, 0, UTC);
  private static final Pattern DEDUP_KEY = Pattern.compile("\"dedup_key\":\"([^\"]*)\"");
  private static final Pattern UUID_SHAPE = Pattern.compile(
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

  private HttpServer server;
  private final List<String> bodies = new CopyOnWriteArrayList<>();

  private PagerDutyEventClient serve() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        bodies.add(exchange.getRequestURI().getPath() + ' '
            + new String(exchange.getRequestBody().readAllBytes(), UTF_8));
        final var bytes = """
            {"status":"success","message":"Event processed"}""".getBytes(UTF_8);
        exchange.sendResponseHeaders(202, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return PagerDutyEventClient.clientBuilder()
        .endpoint("http://" + address.getHostString() + ':' + address.getPort())
        .defaultClientName("handoff-client")
        .defaultRoutingKey("handoff-rk")
        .createClient();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private static PagerDutyChangeEventPayload control() {
    return PagerDutyChangeEventPayload.build().summary("control").timestamp(TIMESTAMP).create();
  }

  private static final String CONTROL_BODY = """
      /v2/change/enqueue {"routing_key":"handoff-rk","payload":{"summary":"control","timestamp":"2019-09-02T03:04:05Z"}}""";

  /// A refusal is synchronous, so a control event sent after it must be the only request the
  /// server ever sees.
  private void assertOnlyTheControlWasSent(final PagerDutyEventClient client) {
    client.defaultRouteChangeEvent(control()).join();
    assertEquals(List.of(CONTROL_BODY), bodies);
  }

  @Test
  void aBuilderWithOnlyATimestampIsRefusedBeforeAnythingIsSent() throws IOException {
    final var client = serve();
    final var trigger = assertThrows(NullPointerException.class,
        () -> client.triggerDefaultRouteEvent(PagerDutyEventPayload.build().timestamp(TIMESTAMP)));
    assertEquals("'Summary' is a required payload field.", trigger.getMessage());
    final var change = assertThrows(NullPointerException.class,
        () -> client.defaultRouteChangeEvent(PagerDutyChangeEventPayload.build().timestamp(TIMESTAMP)));
    assertEquals("'Summary' is a required payload field.", change.getMessage());
    assertOnlyTheControlWasSent(client);
  }

  @Test
  void aNullPayloadIsRefusedBeforeAnythingIsSent() throws IOException {
    final var client = serve();
    final var trigger = assertThrows(NullPointerException.class,
        () -> client.triggerEvent("handoff-rk", (PagerDutyEventPayload) null));
    assertEquals("Payload is a required field.", trigger.getMessage());
    final var change = assertThrows(NullPointerException.class,
        () -> client.changeEvent("handoff-rk", null));
    assertEquals("Payload is a required field.", change.getMessage());
    assertOnlyTheControlWasSent(client);
  }

  @Test
  void anEventBuilderHandedToChangeEventIsCreatedByItsOwnRules() throws IOException {
    final var client = serve();
    final var noSeverity = assertThrows(NullPointerException.class,
        () -> client.defaultRouteChangeEvent(PagerDutyEventPayload.build()
            .summary("change-summary").source("change-source").timestamp(TIMESTAMP)));
    assertEquals("'Severity' is a required payload field.", noSeverity.getMessage());
    final var noSource = assertThrows(NullPointerException.class,
        () -> client.defaultRouteChangeEvent(PagerDutyEventPayload.build()
            .summary("change-summary").severity(PagerDutySeverity.info).timestamp(TIMESTAMP)));
    assertEquals("'Source' is a required payload field.", noSource.getMessage());
    assertOnlyTheControlWasSent(client);
  }

  private static PagerDutyEventPayload.Builder completeEventBuilder() {
    return PagerDutyEventPayload.build()
        .dedupKey("handoff-key")
        .summary("handoff-summary")
        .source("handoff-source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .component("handoff-component")
        .customDetails("handoff-detail", 7)
        .link(PagerDutyLinkRef.build().href("https://link.example").text("link-text"))
        .image(PagerDutyImageRef.build().src("https://image.example/i.png").create());
  }

  @Test
  void aCompleteEventBuilderSendsExactlyTheJsonItsCreateWould() throws IOException {
    final var client = serve();
    client.triggerDefaultRouteEvent(completeEventBuilder()).join();
    client.triggerDefaultRouteEvent(completeEventBuilder().create()).join();
    final var expected = """
        /v2/enqueue {"event_action":"trigger","routing_key":"handoff-rk","dedup_key":"handoff-key",\
        "payload":{"summary":"handoff-summary","source":"handoff-source","severity":"warning",\
        "timestamp":"2019-09-02T03:04:05Z","component":"handoff-component","custom_details":{"handoff-detail":7}},\
        "client":"handoff-client","images":[{"src":"https://image.example/i.png"}],\
        "links":[{"href":"https://link.example","text":"link-text"}]}""";
    assertEquals(List.of(expected, expected), bodies);
  }

  @Test
  void aCompleteChangeBuilderSendsExactlyTheJsonItsCreateWould() throws IOException {
    final var client = serve();
    final var builder = PagerDutyChangeEventPayload.build()
        .summary("change-summary")
        .source("change-source")
        .timestamp(TIMESTAMP)
        .customDetails("change-detail", "v")
        .link(PagerDutyLinkRef.build().href("https://link.example").create())
        .image(PagerDutyImageRef.build().src("https://image.example/i.png").alt("alt-text"));
    client.changeEvent("change-rk", builder).join();
    client.changeEvent("change-rk", builder.create()).join();
    final var expected = """
        /v2/change/enqueue {"routing_key":"change-rk","payload":{"summary":"change-summary","source":"change-source",\
        "timestamp":"2019-09-02T03:04:05Z","custom_details":{"change-detail":"v"}},\
        "links":[{"href":"https://link.example"}],"images":[{"src":"https://image.example/i.png","alt":"alt-text"}]}""";
    assertEquals(List.of(expected, expected), bodies);
  }

  @Test
  void aCompleteEventBuilderHandedToChangeEventSendsTheEventShape() throws IOException {
    final var client = serve();
    client.changeEvent("change-rk", completeEventBuilder()).join();
    final var expected = """
        /v2/change/enqueue {"routing_key":"change-rk","payload":{"summary":"handoff-summary","source":"handoff-source",\
        "severity":"warning","timestamp":"2019-09-02T03:04:05Z","component":"handoff-component",\
        "custom_details":{"handoff-detail":7}},\
        "links":[{"href":"https://link.example","text":"link-text"}],"images":[{"src":"https://image.example/i.png"}]}""";
    assertEquals(List.of(expected), bodies);
  }

  @Test
  void aBuilderWithoutADedupKeyGetsOneKeyAndTimestampForEveryHandOff() throws IOException {
    final var client = serve();
    final var builder = PagerDutyEventPayload.build()
        .summary("repeat-summary")
        .source("repeat-source")
        .severity(PagerDutySeverity.error);
    client.triggerDefaultRouteEvent(builder).join();
    client.triggerDefaultRouteEvent(builder).join();
    assertEquals(2, bodies.size());
    assertEquals(bodies.get(0), bodies.get(1), "the same key and the same defaulted timestamp both times");
    final var matcher = DEDUP_KEY.matcher(bodies.get(0));
    assertTrue(matcher.find(), bodies.get(0));
    final var sentKey = matcher.group(1);
    assertTrue(UUID_SHAPE.matcher(sentKey).matches(), sentKey);
    assertEquals(sentKey, builder.dedupKey(), "the caller's builder itself was created");
    assertNotNull(builder.timestamp(), "the caller's builder keeps its defaulted timestamp");
  }

  /// A change event carries no dedup key, so its timestamp is the only generated value: a
  /// plain change builder must be created in place too, or each hand-off (and each service
  /// retry) would send a freshly defaulted timestamp and the caller's builder would never get
  /// one.
  @Test
  void aChangeBuilderWithoutATimestampGetsOneTimestampForEveryHandOff() throws IOException {
    final var client = serve();
    final var builder = PagerDutyChangeEventPayload.build().summary("s");
    client.changeEvent("change-rk", builder).join();
    client.changeEvent("change-rk", builder).join();
    assertEquals(2, bodies.size());
    assertEquals(bodies.get(0), bodies.get(1), "the same defaulted timestamp both times");
    assertNotNull(builder.timestamp(), "the caller's builder itself was created");
  }

  @Test
  void aCallerImplementedChangePayloadIsSentAsGiven() throws IOException {
    final var client = serve();
    final var payload = new PagerDutyChangeEventPayload() {
      @Override
      public ZonedDateTime timestamp() {
        return null;
      }

      @Override
      public String summary() {
        return null;
      }

      @Override
      public String source() {
        return null;
      }

      @Override
      public Map<String, Object> customDetails() {
        return Map.of();
      }

      @Override
      public List<PagerDutyLinkRef> links() {
        return List.of();
      }

      @Override
      public String payloadJson() {
        return """
            {"summary":"caller-json"}""";
      }
    };
    client.changeEvent("change-rk", payload).join();
    assertEquals(List.of("""
        /v2/change/enqueue {"routing_key":"change-rk","payload":{"summary":"caller-json"}}"""), bodies);
  }
}
