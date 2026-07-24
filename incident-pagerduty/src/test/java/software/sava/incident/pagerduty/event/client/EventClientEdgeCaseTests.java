package software.sava.incident.pagerduty.event.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;
import software.sava.incident.pagerduty.exceptions.PagerDutyRequestException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

/// Wire-level edge cases the happy-path [EventClientTests] cannot reach: the response
/// status boundaries of the success/error routing, and the blank-vs-present directions
/// of the optional `client` / `client_url` trigger fields.
public final class EventClientEdgeCaseTests implements EventClientTest {

  private static final String PAYLOAD_JSON =
      "{\"summary\":\"edge-summary\",\"source\":\"edge-source\",\"severity\":\"info\",\"timestamp\":\"2018-08-01T02:03:04Z\"}";

  private void writeStatus(final HttpExchange httpExchange, final int statusCode, final String body) {
    final var bytes = body.getBytes(UTF_8);
    try {
      httpExchange.sendResponseHeaders(statusCode, bytes.length);
      try (final var os = httpExchange.getResponseBody()) {
        os.write(bytes);
      }
    } catch (final IOException ioEx) {
      throw new UncheckedIOException(ioEx);
    }
  }

  @Override
  public void createContext(final HttpServer httpServer, final BiConsumer<String, HttpHandler> server) {
    final int port = httpServer.getAddress().getPort();
    final var routingKey = "routing-key-" + port;
    server.accept("/v2/enqueue", httpExchange -> {
      final var body = new String(httpExchange.getRequestBody().readAllBytes(), UTF_8);
      if (body.contains("\"dedup_key\":\"edge-300\"")) {
        // just below the 5xx band and outside 2xx: must route through the error path
        writeStatus(httpExchange, 300, """
            {"status":"redirected","message":"Multiple Choices"}""");
      } else if (body.contains("\"dedup_key\":\"edge-url\"")) {
        // blank client name is omitted; the client_url still serializes
        assertEquals(String.format("""
                {"event_action":"trigger","routing_key":"%s","dedup_key":"edge-url","payload":%s,"client_url":"https://client.example"}""",
            routingKey, PAYLOAD_JSON), body);
        writeStatus(httpExchange, 200, """
            {"status":"success","message":"Event processed","dedup_key":"edge-url"}""");
      } else if (body.contains("\"dedup_key\":\"edge-name\"")) {
        // blank client_url is omitted; the client name still serializes
        assertEquals(String.format("""
                {"event_action":"trigger","routing_key":"%s","dedup_key":"edge-name","payload":%s,"client":"edge-client-name"}""",
            routingKey, PAYLOAD_JSON), body);
        writeStatus(httpExchange, 200, """
            {"status":"success","message":"Event processed","dedup_key":"edge-name"}""");
      } else if (body.contains("\"dedup_key\":\"edge-none\"")) {
        // null client name and url are both omitted
        assertEquals(String.format("""
                {"event_action":"trigger","routing_key":"%s","dedup_key":"edge-none","payload":%s}""",
            routingKey, PAYLOAD_JSON), body);
        writeStatus(httpExchange, 200, """
            {"status":"success","message":"Event processed","dedup_key":"edge-none"}""");
      } else {
        fail("Unexpected request body: " + body);
      }
    });
  }

  private static PagerDutyEventPayload payload(final String dedupKey) {
    return PagerDutyEventPayload.build()
        .dedupKey(dedupKey)
        .summary("edge-summary")
        .source("edge-source")
        .severity(PagerDutySeverity.info)
        .timestamp(ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, UTC))
        .create();
  }

  @Override
  public void test(final PagerDutyEventClient client) {
    final var urlResponse = client
        .triggerEvent(" ", "https://client.example", client.defaultRoutingKey(), payload("edge-url"))
        .join();
    assertEquals("success", urlResponse.status());
    assertEquals("edge-url", urlResponse.dedupKey());

    final var nameResponse = client
        .triggerEvent("edge-client-name", " ", client.defaultRoutingKey(), payload("edge-name"))
        .join();
    assertEquals("success", nameResponse.status());
    assertEquals("edge-name", nameResponse.dedupKey());

    final var noneResponse = client
        .triggerEvent(null, null, client.defaultRoutingKey(), payload("edge-none"))
        .join();
    assertEquals("success", noneResponse.status());
    assertEquals("edge-none", noneResponse.dedupKey());

    final var thrown = assertThrows(CompletionException.class,
        () -> client.acknowledgeEvent("edge-300").join());
    final var requestException = assertInstanceOf(PagerDutyRequestException.class, thrown.getCause());
    assertEquals("Multiple Choices", requestException.getMessage());
    assertEquals("redirected", requestException.status());
    assertFalse(requestException.canBeRetried());
  }
}
