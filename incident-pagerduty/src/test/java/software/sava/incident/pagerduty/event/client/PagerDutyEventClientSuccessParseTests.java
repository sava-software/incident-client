package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;
import software.sava.incident.pagerduty.event.service.PagerDutyService;
import software.sava.incident.pagerduty.exceptions.PagerDutyParseException;
import software.sava.incident.pagerduty.exceptions.PagerDutyRequestException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// Pins what the event client does with a 2xx response whose body cannot be parsed.
/// Property: the Events API's 202 means the event was accepted and must not be sent again,
/// so a body the client cannot read fails with a [PagerDutyParseException] that is not
/// retryable, keeps the parser's failure as its cause, and names the status and body;
/// a body that does parse, even one carrying no members, is still a success.
/// Oracle: the contract's retry table (202: do not retry) and hand-written expected messages.
final class PagerDutyEventClientSuccessParseTests {

  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  /// The status every request is answered with.
  private int status = 202;

  /// Serves every request with a 202 carrying `body`; a zero-length body is sent with a
  /// declared length of `-1`, which `jdk.httpserver` reads as "no body".
  private PagerDutyEventClient serve(final String body) throws IOException {
    return serve(body, null);
  }

  /// As [#serve(String)], declaring `contentEncoding` as the response's `Content-Encoding`
  /// when it is not null.
  private PagerDutyEventClient serve(final String body, final String contentEncoding) throws IOException {
    return serve(body, contentEncoding, null);
  }

  /// As [#serve(String, String)], with the client built with `testResponse` when it is not
  /// null.
  private PagerDutyEventClient serve(final String body,
                                     final String contentEncoding,
                                     final BiPredicate<HttpResponse<?>, byte[]> testResponse) throws IOException {
    return serve(body.getBytes(UTF_8), contentEncoding, testResponse);
  }

  /// As [#serve(String, String, BiPredicate)], with the body's bytes as they go on the wire.
  private PagerDutyEventClient serve(final byte[] bytes,
                                     final String contentEncoding,
                                     final BiPredicate<HttpResponse<?>, byte[]> testResponse) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        requests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        if (contentEncoding != null) {
          exchange.getResponseHeaders().set("Content-Encoding", contentEncoding);
        }
        if (bytes.length == 0) {
          exchange.sendResponseHeaders(status, -1);
        } else {
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
        }
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return PagerDutyEventClient.clientBuilder()
        .endpoint("http://" + address.getHostString() + ':' + address.getPort())
        .defaultRoutingKey("parse-rk")
        .testResponse(testResponse)
        .createClient();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private PagerDutyParseException parseFailure(final PagerDutyEventClient client) {
    final var thrown = assertThrows(CompletionException.class,
        () -> client.acknowledgeEvent("parse-key").join());
    return assertInstanceOf(PagerDutyParseException.class, thrown.getCause());
  }

  @Test
  void aZeroByteAcceptedBodyFailsWithoutRetry() throws IOException {
    final var client = serve("");
    final var thrown = parseFailure(client);
    assertEquals("Failed to adapt 202 response: ''", thrown.getMessage());
    assertFalse(thrown.canBeRetried());
    assertEquals(202, thrown.httpResponse().statusCode());
    assertNotNull(thrown.getCause(), "the parser's failure is kept as the cause");
    assertFalse(thrown.getCause() instanceof PagerDutyParseException, "the parse failure is wrapped once");
    assertEquals(1, requests.get());
  }

  @Test
  void aNonJsonAcceptedBodyFailsWithoutRetry() throws IOException {
    final var client = serve("<html>accepted</html>");
    final var thrown = parseFailure(client);
    assertEquals("Failed to adapt 202 response: '<html>accepted</html>'", thrown.getMessage());
    assertFalse(thrown.canBeRetried());
    assertEquals(202, thrown.httpResponse().statusCode());
    assertNotNull(thrown.getCause(), "the parser's failure is kept as the cause");
    assertFalse(thrown.getCause() instanceof PagerDutyParseException, "the parse failure is wrapped once");
    assertEquals(1, requests.get());
  }

  /// A 202 whose body cannot even be read (marked gzip, not gzip) was still accepted: the
  /// read failure is wrapped like a parse failure, with no body text to name.
  @Test
  void anUnreadableAcceptedBodyFailsWithoutRetry() throws IOException {
    final var client = serve("{\"status\":\"success\"}", "gzip");
    final var thrown = parseFailure(client);
    assertEquals("Failed to adapt 202 response: ''", thrown.getMessage());
    assertFalse(thrown.canBeRetried());
    assertEquals(202, thrown.httpResponse().statusCode());
    assertInstanceOf(UncheckedIOException.class, thrown.getCause(), "the read failure is kept as the cause");
    assertEquals(1, requests.get());
  }

  @Test
  void anUnreadableAcceptedChangeEventIsPostedOnceThroughTheService() throws IOException {
    final var client = serve("{\"status\":\"success\"}", "gzip");
    final var service = PagerDutyService.build()
        .client(client)
        .eventPrototype(PagerDutyEventPayload.build()
            .summary("prototype-summary")
            .source("prototype-source")
            .severity(PagerDutySeverity.error)
            .create())
        .create();
    final var change = PagerDutyChangeEventPayload.build().summary("change-summary").create();
    final var thrown = assertThrows(CompletionException.class,
        () -> service.changeEvent(change, 3, 0, 0, TimeUnit.MILLISECONDS).join());
    final var parseException = assertInstanceOf(PagerDutyParseException.class, thrown.getCause());
    assertFalse(parseException.canBeRetried());
    assertInstanceOf(UncheckedIOException.class, parseException.getCause());
    assertEquals(1, requests.get(), "an accepted change event is posted once");
  }

  /// A response predicate makes the transport read the body before the parser runs. A 202
  /// whose body cannot be read is still an accepted event: the predicate, which has no body
  /// to judge, is not consulted, and the failure is the same parse failure, not a transport
  /// failure the retry wrappers would send again.
  @Test
  void anUnreadableAcceptedBodyFailsWithoutRetryUnderAResponsePredicate() throws IOException {
    final var judged = new AtomicInteger();
    final var client = serve("{\"status\":\"success\"}", "gzip", (_, _) -> judged.incrementAndGet() > 0);
    final var thrown = parseFailure(client);
    assertEquals("Failed to adapt 202 response: ''", thrown.getMessage());
    assertFalse(thrown.canBeRetried());
    assertEquals(202, thrown.httpResponse().statusCode());
    assertInstanceOf(UncheckedIOException.class, thrown.getCause(), "the read failure is kept as the cause");
    assertEquals(1, requests.get());
    assertEquals(0, judged.get(), "there is no body for the predicate to judge");
  }

  @Test
  void anUnreadableAcceptedChangeEventIsPostedOnceThroughTheServiceUnderAResponsePredicate() throws IOException {
    final var client = serve("{\"status\":\"success\"}", "gzip", (_, _) -> true);
    final var service = PagerDutyService.build()
        .client(client)
        .eventPrototype(PagerDutyEventPayload.build()
            .summary("prototype-summary")
            .source("prototype-source")
            .severity(PagerDutySeverity.error)
            .create())
        .create();
    final var change = PagerDutyChangeEventPayload.build().summary("change-summary").create();
    final var thrown = assertThrows(CompletionException.class,
        () -> service.changeEvent(change, 3, 0, 0, TimeUnit.MILLISECONDS).join());
    final var parseException = assertInstanceOf(PagerDutyParseException.class, thrown.getCause());
    assertFalse(parseException.canBeRetried());
    assertEquals(1, requests.get(), "an accepted change event is posted once");
  }

  /// An error status whose body cannot be read is handled as it is without a predicate too:
  /// a 429 fails with the request exception its status alone decides, which can be retried,
  /// and the predicate is not consulted.
  @Test
  void anUnreadableErrorBodyIsHandledAsWithoutAResponsePredicate() throws IOException {
    status = 429;
    final var judged = new AtomicInteger();
    final var predicates = new java.util.ArrayList<BiPredicate<HttpResponse<?>, byte[]>>();
    predicates.add(null);
    predicates.add((_, _) -> judged.incrementAndGet() > 0);
    for (final var predicate : predicates) {
      final var client = serve("{\"status\":\"throttled\"}", "gzip", predicate);
      final var thrown = assertThrows(CompletionException.class,
          () -> client.acknowledgeEvent("parse-key").join());
      final var refused = assertInstanceOf(PagerDutyRequestException.class, thrown.getCause());
      assertEquals(429, refused.httpResponse().statusCode());
      assertEquals("Too many requests", refused.getMessage());
      assertTrue(refused.canBeRetried());
      stop();
    }
    assertEquals(2, requests.get());
    assertEquals(0, judged.get(), "there is no body for the predicate to judge");
  }

  /// The predicate is handed the body decoded: a gzip-encoded 202 reaches it inflated, once,
  /// and is parsed.
  @Test
  void aGzipEncodedBodyReachesTheResponsePredicateDecoded() throws IOException {
    final var json = "{\"status\":\"success\",\"dedup_key\":\"parse-key\"}";
    final var compressed = new java.io.ByteArrayOutputStream();
    try (final var gzip = new java.util.zip.GZIPOutputStream(compressed)) {
      gzip.write(json.getBytes(UTF_8));
    }
    final var seen = new java.util.ArrayList<String>();
    final var client = serve(compressed.toByteArray(), "gzip", (_, body) -> {
      seen.add(new String(body, UTF_8));
      return true;
    });
    assertEquals("success", client.acknowledgeEvent("parse-key").join().status());
    assertEquals(java.util.List.of(json), seen);
  }

  /// A body the predicate can read is judged as before: the predicate sees the body once,
  /// an accepted response is parsed, a rejected one completes with null, and a body that
  /// reads but does not parse is the parse failure it is without a predicate.
  @Test
  void aReadableBodyIsStillJudgedByTheResponsePredicate() throws IOException {
    final var seen = new java.util.ArrayList<String>();
    final var accepting = serve("{\"status\":\"success\",\"dedup_key\":\"parse-key\"}", null, (_, body) -> {
      seen.add(new String(body, UTF_8));
      return true;
    });
    assertEquals("success", accepting.acknowledgeEvent("parse-key").join().status());
    assertEquals(java.util.List.of("{\"status\":\"success\",\"dedup_key\":\"parse-key\"}"), seen);
    stop();

    final var rejecting = serve("{\"status\":\"success\"}", null, (_, _) -> false);
    assertNull(rejecting.acknowledgeEvent("parse-key").join());
    stop();

    final var unparsed = parseFailure(serve("<html>accepted</html>", null, (_, _) -> true));
    assertEquals("Failed to adapt 202 response: '<html>accepted</html>'", unparsed.getMessage());
    assertFalse(unparsed.canBeRetried());
  }

  @Test
  void anEmptyAcceptedObjectIsStillASuccess() throws IOException {
    final var client = serve("{}");
    final var response = client.acknowledgeEvent("parse-key").join();
    assertNull(response.status());
    assertNull(response.message());
    assertNull(response.dedupKey());
  }

  @Test
  void aNullAcceptedBodyIsStillASuccess() throws IOException {
    final var client = serve("null");
    final var response = client.acknowledgeEvent("parse-key").join();
    assertNull(response.status());
    assertNull(response.message());
    assertNull(response.dedupKey());
  }
}
