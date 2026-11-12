package software.sava.incident.io.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.io.exceptions.IncidentIoParseException;
import software.sava.incident.io.exceptions.IncidentIoRequestException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Pins `IncidentIoAlertEventClientImpl` against real responses: the request it sends, the
/// status gate, error and rate-limit mapping, and the parse-failure path.
final class IncidentIoAlertEventClientWireTests {

  private HttpServer server;

  private record Wire(String endpoint,
                      AtomicReference<String> method,
                      AtomicReference<String> path,
                      AtomicReference<Map<String, List<String>>> headers,
                      AtomicReference<String> body) {
  }

  private Wire serve(final int status, final String responseBody) throws IOException {
    return serve(status, responseBody, Map.of());
  }

  private Wire serve(final int status,
                     final String responseBody,
                     final Map<String, String> responseHeaders) throws IOException {
    final var method = new AtomicReference<String>();
    final var path = new AtomicReference<String>();
    final var headers = new AtomicReference<Map<String, List<String>>>();
    final var body = new AtomicReference<String>();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().getRawPath());
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        headers.set(Map.copyOf(exchange.getRequestHeaders()));
        final var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        responseHeaders.forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
          exchange.getResponseBody().write(bytes);
        }
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return new Wire("http://" + address.getHostString() + ':' + address.getPort() + "/v2/alert_events/http/src-1",
        method, path, headers, body);
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private static IncidentIoAlertEventClient client(final Wire wire) {
    return IncidentIoAlertEventClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .token("source-secret")
        .createClient();
  }

  private static AlertEvent event() {
    return AlertEvent.build()
        .title("Disk full")
        .status(AlertEvent.Status.firing)
        .deduplicationKey("key-1")
        .description("**db-1** is full")
        .sourceUrl("https://monitor.example.com/1")
        .metadata("service", "db")
        .create();
  }

  private static final String ACCEPTED = """
      {"deduplication_key":"key-1","message":"Event accepted for processing","status":"success"}""";

  @Test
  void theRequestAndAnAcceptedResponse() throws IOException {
    final var wire = serve(202, ACCEPTED);
    final var event = event();

    final var response = client(wire).sendAlertEvent(event).join();

    assertEquals("success", response.status());
    assertEquals("Event accepted for processing", response.message());
    assertEquals("key-1", response.deduplicationKey());

    assertEquals("POST", wire.method().get());
    assertEquals("/v2/alert_events/http/src-1", wire.path().get());
    final var headers = wire.headers().get();
    assertEquals(List.of("Bearer source-secret"), headers.get("Authorization"));
    assertEquals(List.of("application/json"), headers.get("Content-type"));
    assertEquals("""
        {"title":"Disk full","status":"firing","deduplication_key":"key-1",\
        "description":"**db-1** is full","source_url":"https://monitor.example.com/1",\
        "metadata":{"service":"db"}}""", wire.body().get());
    assertEquals(event.body(), wire.body().get());
  }

  @Test
  void twoHundredIsTheLowerEdgeOfSuccess() throws IOException {
    final var wire = serve(200, """
        {"status":"ok-200","message":"m","deduplication_key":"k-200"}""");
    final var response = client(wire).sendAlertEvent(event()).join();
    assertEquals("ok-200", response.status());
    assertEquals("k-200", response.deduplicationKey());
  }

  @Test
  void threeHundredIsAFailure() throws IOException {
    final var wire = serve(300, """
        {"type":"multiple_choices","status":300,"request_id":"req-300","errors":[]}""");
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertEquals(300, cause.httpResponse().statusCode());
  }

  @Test
  void anErrorEnvelopeBecomesARequestException() throws IOException {
    final var wire = serve(401, """
        {"type":"authentication_error","status":401,"request_id":"req-401",\
        "errors":[{"code":"unauthorized","message":"bad token"}]}""");
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertEquals(401, cause.httpResponse().statusCode());
    assertEquals("authentication_error", cause.type());
    assertEquals("req-401", cause.requestId());
    assertEquals(401, cause.errorCode());
    assertEquals(List.of("unauthorized: bad token"), cause.errors());
    assertFalse(cause.canBeRetried());
    assertNull(cause.retryAfter());
  }

  private static final String RATE_LIMITED_PAST = """
      {"type":"too_many_requests","status":429,"request_id":"req-429","errors":[],\
      "rate_limit":{"name":"alert_source","limit":10,"remaining":0,"retry_after":"2000-01-01T00:00:00Z"}}""";

  @Test
  void aRateLimitHeaderWinsOverTheBody() throws IOException {
    final var wire = serve(429, RATE_LIMITED_PAST, Map.of("Retry-After", "30"));
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertTrue(cause.canBeRetried());
    assertEquals(Duration.ofSeconds(30), cause.retryAfter());
  }

  /// No header: the body's `rate_limit.retry_after` is measured from the response's `Date`.
  /// The instant lies before any `Date` the server can send, so the wait is zero rather than
  /// null, whatever the server's clock reads.
  @Test
  void aRateLimitBodyWithoutTheHeaderIsMeasuredFromTheDate() throws IOException {
    final var wire = serve(429, RATE_LIMITED_PAST);
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertTrue(cause.httpResponse().headers().firstValue("Date").isPresent(), "the server sends a Date");
    assertEquals("2000-01-01T00:00:00Z", cause.rateLimit().retryAfter());
    assertEquals(Duration.ZERO, cause.retryAfter());
  }

  /// jdk.httpserver always stamps its own `Date`, so the expected wait is read back from the
  /// response the client received rather than from the local clock.
  @Test
  void aFutureRateLimitInstantIsTheTimeFromTheResponseDate() throws IOException {
    final var wire = serve(429, """
        {"type":"too_many_requests","status":429,"request_id":"req-429","errors":[],\
        "rate_limit":{"name":"alert_source","limit":10,"remaining":0,"retry_after":"2999-01-01T00:00:00Z"}}""");
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    final var date = OffsetDateTime.parse(cause.httpResponse().headers().firstValue("Date").orElseThrow(),
        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    assertEquals(Duration.between(date, Instant.parse("2999-01-01T00:00:00Z")), cause.retryAfter());
  }

  @Test
  void aRateLimitWithNeitherCarriesNoWait() throws IOException {
    final var wire = serve(429, """
        {"type":"too_many_requests","status":429,"request_id":"req-429","errors":[]}""");
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertTrue(cause.canBeRetried());
    assertNull(cause.rateLimit());
    assertNull(cause.retryAfter());
  }

  @Test
  void anUnparseableSuccessBodyIsAParseExceptionThatIsNotRetried() throws IOException {
    final var wire = serve(202, "not json");
    final var thrown = assertThrows(CompletionException.class, () -> client(wire).sendAlertEvent(event()).join());
    final var cause = assertInstanceOf(IncidentIoParseException.class, thrown.getCause());
    assertEquals("Failed to adapt 202 response: 'not json'", cause.getMessage());
    assertNotNull(cause.getCause(), "the underlying parse error must be retained");
    assertFalse(cause.canBeRetried());
    assertEquals(202, cause.httpResponse().statusCode());
  }

  @Test
  void sendAlertEventReturnsTheTransportFuture() throws IOException {
    final var wire = serve(202, ACCEPTED);
    final CompletableFuture<AlertEventResponse> future = client(wire).sendAlertEvent(event());
    assertNotNull(future);
    assertEquals("key-1", future.join().deduplicationKey());
  }

  @Test
  void theClientRequestTimeoutIsApplied() throws IOException {
    final var wire = serve(202, ACCEPTED);
    final var seen = new AtomicReference<Optional<Duration>>();
    final var client = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .requestTimeout(Duration.ofSeconds(17))
        .extendRequest(builder -> {
          seen.set(builder.copy().build().timeout());
          return builder;
        })
        .createClient();
    assertNotNull(client.sendAlertEvent(event()).join());
    assertEquals(Optional.of(Duration.ofSeconds(17)), seen.get());

    final var defaulted = IncidentIoAlertEventClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .extendRequest(builder -> {
          seen.set(builder.copy().build().timeout());
          return builder;
        })
        .createClient();
    assertNotNull(defaulted.sendAlertEvent(event()).join());
    assertEquals(Optional.of(Duration.ofSeconds(8)), seen.get(), "the builder's default request timeout");
  }

  @Test
  void theTokenDecoratorReturnsTheBuilderItDecorated() {
    final var request = IncidentIoAlertEventClient.clientBuilder()
        .token("t-2")
        .extendRequest()
        .apply(HttpRequest.newBuilder(URI.create("http://127.0.0.1/")))
        .build();
    assertEquals(Optional.of("Bearer t-2"), request.headers().firstValue("Authorization"));
  }
}
