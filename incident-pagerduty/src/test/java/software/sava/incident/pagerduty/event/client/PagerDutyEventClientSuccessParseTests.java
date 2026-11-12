package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;
import software.sava.incident.pagerduty.event.service.PagerDutyService;
import software.sava.incident.pagerduty.exceptions.PagerDutyParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

  /// Serves every request with a 202 carrying `body`; a zero-length body is sent with a
  /// declared length of `-1`, which `jdk.httpserver` reads as "no body".
  private PagerDutyEventClient serve(final String body) throws IOException {
    return serve(body, null);
  }

  /// As [#serve(String)], declaring `contentEncoding` as the response's `Content-Encoding`
  /// when it is not null.
  private PagerDutyEventClient serve(final String body, final String contentEncoding) throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        requests.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        if (contentEncoding != null) {
          exchange.getResponseHeaders().set("Content-Encoding", contentEncoding);
        }
        final var bytes = body.getBytes(UTF_8);
        if (bytes.length == 0) {
          exchange.sendResponseHeaders(202, -1);
        } else {
          exchange.sendResponseHeaders(202, bytes.length);
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
