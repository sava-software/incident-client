package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

/// Pins the client-side request body limit. Property: every body the event client posts
/// (trigger, acknowledge, resolve, change) is measured in UTF-8 bytes, the encoding the
/// transport sends; a body of exactly 512 KiB (524,288 bytes) is sent, and one byte more is
/// refused with an [IllegalArgumentException], synchronously, before any request is made.
/// Oracle: hand-written request templates whose UTF-8 length fixes the pad size, the byte
/// count a `jdk.httpserver` actually received, and `512 * 1024` from the vendor's "512 KB".
final class PagerDutyEventClientSizeLimitTests {

  private static final int LIMIT = 512 * 1024;
  // zero nano field: a defaulted timestamp prints 0-9 fraction digits and would move the size
  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2019, 9, 2, 3, 4, 5, 0, UTC);
  private static final String PAD = "%PAD%";
  // runs of each multi-byte width; the rest of a pad is ASCII filler
  private static final int RUN = 30_000;
  private static final String TWO_BYTE = "é";
  private static final String THREE_BYTE = "€";
  private static final String FOUR_BYTE = "😀";

  private static final String TRIGGER_TEMPLATE = """
      {"event_action":"trigger","routing_key":"size-rk","dedup_key":"size-key",\
      "payload":{"summary":"size-summary","source":"size-source","severity":"info",\
      "timestamp":"2019-09-02T03:04:05Z","custom_details":{"pad":"%PAD%"}}}""";
  private static final String CHANGE_TEMPLATE = """
      {"routing_key":"size-rk","payload":{"summary":"size-summary","source":"size-source",\
      "timestamp":"2019-09-02T03:04:05Z","custom_details":{"pad":"%PAD%"}}}""";
  private static final String ACKNOWLEDGE_TEMPLATE = """
      {"routing_key":"size-rk","dedup_key":"%PAD%","event_action":"acknowledge"}""";
  private static final String RESOLVE_TEMPLATE = """
      {"routing_key":"size-rk","dedup_key":"%PAD%","event_action":"resolve"}""";

  private record Received(String path, int bytes, String body) {
  }

  private HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();

  private PagerDutyEventClient serve() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        final var bytes = exchange.getRequestBody().readAllBytes();
        received.add(new Received(exchange.getRequestURI().getPath(), bytes.length, new String(bytes, UTF_8)));
        final var response = """
            {"status":"success","message":"Event processed"}""".getBytes(UTF_8);
        exchange.sendResponseHeaders(202, response.length);
        exchange.getResponseBody().write(response);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return PagerDutyEventClient.clientBuilder()
        .endpoint("http://" + address.getHostString() + ':' + address.getPort())
        .defaultRoutingKey("size-rk")
        .createClient();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private static int utf8(final String str) {
    return str.getBytes(UTF_8).length;
  }

  /// A pad of exactly `padBytes` UTF-8 bytes: a run each of 2-, 3- and 4-byte (surrogate
  /// pair) characters, then `tail`, then ASCII filler.
  private static String pad(final int padBytes, final String tail) {
    final var runs = TWO_BYTE.repeat(RUN) + THREE_BYTE.repeat(RUN) + FOUR_BYTE.repeat(RUN);
    final int filler = padBytes - utf8(runs) - utf8(tail);
    assertTrue(filler > 0, "the pad leaves room for ASCII filler");
    final var pad = runs + tail + "a".repeat(filler);
    assertEquals(padBytes, utf8(pad));
    return pad;
  }

  /// The template with a pad that brings its UTF-8 length to `bodyBytes`.
  private static String body(final String template, final int bodyBytes, final String tail) {
    final var body = template.replace(PAD, pad(bodyBytes - utf8(template.replace(PAD, "")), tail));
    assertEquals(bodyBytes, utf8(body));
    return body;
  }

  /// Extracts the pad back out of a body built by [#body].
  private static String padOf(final String template, final String body) {
    final int start = template.indexOf(PAD);
    final int suffix = template.length() - start - PAD.length();
    return body.substring(start, body.length() - suffix);
  }

  private static PagerDutyEventPayload trigger(final String pad) {
    return PagerDutyEventPayload.build()
        .dedupKey("size-key")
        .summary("size-summary")
        .source("size-source")
        .severity(PagerDutySeverity.info)
        .timestamp(TIMESTAMP)
        .customDetails("pad", pad)
        .create();
  }

  private static PagerDutyChangeEventPayload change(final String pad) {
    return PagerDutyChangeEventPayload.build()
        .summary("size-summary")
        .source("size-source")
        .timestamp(TIMESTAMP)
        .customDetails("pad", pad)
        .create();
  }

  private static final String CONTROL_BODY = """
      {"routing_key":"size-rk","dedup_key":"control-key","event_action":"acknowledge"}""";

  /// A body of exactly the limit reaches the server byte for byte, and the response is a
  /// success.
  private void assertSentAtTheLimit(final String path, final String template, final Consumer<String> send) {
    final var expected = body(template, LIMIT, "");
    assertTrue(expected.length() < LIMIT, "the multi-byte runs keep String.length() under the limit");
    send.accept(padOf(template, expected));
    assertEquals(1, received.size());
    final var request = received.getFirst();
    assertEquals(path, request.path());
    // sizes first, so a failure does not print a half-megabyte diff
    assertEquals(LIMIT, request.bytes());
    assertTrue(expected.equals(request.body()), "the server received exactly the expected body");
  }

  /// Each body one byte over the limit is refused synchronously with a message naming the
  /// limit and the size, whichever character tips it over: one more ASCII byte, an ASCII byte
  /// swapped for a 2-byte character (same `String.length()`), two for a 3-byte character, or
  /// three for a surrogate pair. Every refused body is under the limit in `String.length()`.
  /// A control request sent afterwards is the only one the server ever sees.
  private void assertRefusedOverTheLimit(final PagerDutyEventClient client,
                                         final String template,
                                         final Consumer<String> send) {
    final int atLimitLength = body(template, LIMIT, "").length();
    for (final var tail : List.of("", TWO_BYTE, THREE_BYTE, FOUR_BYTE)) {
      final var over = body(template, LIMIT + 1, tail);
      assertTrue(over.length() < LIMIT, "refused although String.length() is under the limit");
      assertTrue(over.length() <= atLimitLength + 1);
      final var pad = padOf(template, over);
      final var thrown = assertThrows(IllegalArgumentException.class, () -> send.accept(pad));
      assertEquals("PagerDuty Events API request body is 524289 bytes, over the 524288-byte limit.",
          thrown.getMessage());
    }
    assertTrue(received.isEmpty(), "nothing reached the transport");
    client.acknowledgeEvent("control-key").join();
    assertEquals(List.of(new Received("/v2/enqueue", utf8(CONTROL_BODY), CONTROL_BODY)), received);
  }

  @Test
  void theLimitIsTheLargerReadingOf512Kb() {
    assertEquals(524_288, PagerDutyEventClient.MAX_REQUEST_BODY_BYTES);
  }

  @Test
  void aTriggerBodyOfExactlyTheLimitIsSent() throws IOException {
    final var client = serve();
    assertSentAtTheLimit("/v2/enqueue", TRIGGER_TEMPLATE,
        pad -> client.triggerEvent("size-rk", trigger(pad)).join());
  }

  @Test
  void aTriggerBodyOverTheLimitIsRefusedBeforeSending() throws IOException {
    final var client = serve();
    assertRefusedOverTheLimit(client, TRIGGER_TEMPLATE, pad -> client.triggerEvent("size-rk", trigger(pad)));
  }

  @Test
  void aChangeBodyOfExactlyTheLimitIsSent() throws IOException {
    final var client = serve();
    assertSentAtTheLimit("/v2/change/enqueue", CHANGE_TEMPLATE,
        pad -> client.changeEvent("size-rk", change(pad)).join());
  }

  @Test
  void aChangeBodyOverTheLimitIsRefusedBeforeSending() throws IOException {
    final var client = serve();
    assertRefusedOverTheLimit(client, CHANGE_TEMPLATE, pad -> client.changeEvent("size-rk", change(pad)));
  }

  @Test
  void anAcknowledgeBodyOfExactlyTheLimitIsSent() throws IOException {
    final var client = serve();
    assertSentAtTheLimit("/v2/enqueue", ACKNOWLEDGE_TEMPLATE,
        key -> client.acknowledgeEvent("size-rk", key).join());
  }

  @Test
  void anAcknowledgeBodyOverTheLimitIsRefusedBeforeSending() throws IOException {
    final var client = serve();
    assertRefusedOverTheLimit(client, ACKNOWLEDGE_TEMPLATE, key -> client.acknowledgeEvent("size-rk", key));
  }

  @Test
  void aResolveBodyOfExactlyTheLimitIsSent() throws IOException {
    final var client = serve();
    assertSentAtTheLimit("/v2/enqueue", RESOLVE_TEMPLATE,
        key -> client.resolveEvent("size-rk", key).join());
  }

  @Test
  void aResolveBodyOverTheLimitIsRefusedBeforeSending() throws IOException {
    final var client = serve();
    assertRefusedOverTheLimit(client, RESOLVE_TEMPLATE, key -> client.resolveEvent("size-rk", key));
  }
}
