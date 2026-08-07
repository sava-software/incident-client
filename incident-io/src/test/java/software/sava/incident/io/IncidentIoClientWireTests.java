package software.sava.incident.io;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.incident.io.exceptions.IncidentIoParseException;
import software.sava.incident.io.exceptions.IncidentIoRequestException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Pins `IncidentIoClientImpl` against real responses. The status gate, the parse-failure
/// path and the bearer-token request decorator are only observable on the wire -- no
/// builder accessor reads them back, so nothing else in this module could reach them.
final class IncidentIoClientWireTests {

  private HttpServer server;

  private record Wire(String endpoint,
                      AtomicReference<Map<String, List<String>>> headers,
                      AtomicReference<String> body) {
  }

  private Wire serve(final int status, final String responseBody) throws IOException {
    final var headers = new AtomicReference<Map<String, List<String>>>();
    final var body = new AtomicReference<String>();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        headers.set(Map.copyOf(exchange.getRequestHeaders()));
        final var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    final var address = server.getAddress();
    return new Wire("http://" + address.getHostString() + ':' + address.getPort() + "/v2/incidents",
        headers, body);
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  private static CreateIncidentRequest request() {
    return CreateIncidentRequest.requestBuilder()
        .name("Wire Incident")
        .summary("Wire Summary")
        .idempotencyKey("idem-1")
        .visibility("public")
        .severityId("sev-1")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .mode("standard")
        .build();
  }

  private static final String SUCCESS_BODY = """
      {"incident":{"id":"inc-1","name":"Wire Incident","reference":"INC-1",\
      "permalink":"https://app.incident.io/incidents/1","summary":"Wire Summary",\
      "mode":"standard","visibility":"public","has_debrief":false,\
      "created_at":"2026-08-07T00:00:00Z","updated_at":"2026-08-07T00:00:00Z"}}""";

  @Test
  void aSuccessfulResponseIsParsedIntoTheRecord() throws IOException {
    final var wire = serve(200, SUCCESS_BODY);
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final var response = client.createIncident(request()).join();
    assertNotNull(response, "a 2xx must yield a parsed response, not null");
    assertEquals("inc-1", response.id());
    assertEquals("Wire Incident", response.name());
    assertEquals("INC-1", response.reference());
    assertEquals(CreateIncidentResponse.Mode.standard, response.mode());
    assertEquals(CreateIncidentResponse.Visibility.PUBLIC, response.visibility());

    // the request really carried the serialized body
    assertTrue(wire.body().get().contains("\"name\":\"Wire Incident\""));
  }

  @Test
  void aBearerTokenBecomesAnAuthorizationHeaderOnTheWire() throws IOException {
    final var wire = serve(200, SUCCESS_BODY);
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .bearerToken("secret-token")
        .createClient();

    assertNotNull(client.createIncident(request()).join());

    final var headers = wire.headers().get();
    assertEquals(List.of("Bearer secret-token"), headers.get("Authorization"),
        "the decorator must set Authorization, and must return the builder it decorated");
    assertEquals(List.of("application/json"), headers.get("Content-type"));
  }

  @Test
  void statusesOutsideTwoHundredRangeBecomeRequestExceptions() throws IOException {
    // the gate is `statusCode < 200 || statusCode >= 300`; 400 exercises the upper arm
    final var wire = serve(400, """
        {"type":"validation_error","status":400,"request_id":"req-1",\
        "errors":[{"code":"invalid_value","message":"name is required"}]}""");
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final var thrown = assertThrows(CompletionException.class,
        () -> client.createIncident(request()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertEquals(400, cause.httpResponse().statusCode());
    assertFalse(cause.canBeRetried());
  }

  @Test
  void aServerErrorIsRetriable() throws IOException {
    final var wire = serve(503, """
        {"type":"internal_error","status":503,"request_id":"req-2","errors":[]}""");
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final var thrown = assertThrows(CompletionException.class,
        () -> client.createIncident(request()).join());
    final var cause = assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
    assertTrue(cause.canBeRetried());
  }

  @Test
  void aThreeHundredIsTreatedAsAFailureNotSuccess() throws IOException {
    // 300 is the exact lower edge of the failure range: the boundary must not be >
    final var wire = serve(300, """
        {"type":"multiple_choices","status":300,"request_id":"req-3","errors":[]}""");
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final var thrown = assertThrows(CompletionException.class,
        () -> client.createIncident(request()).join());
    assertInstanceOf(IncidentIoRequestException.class, thrown.getCause());
  }

  @Test
  void aTwoHundredWithAnUnparseableBodyBecomesAParseException() throws IOException {
    final var wire = serve(200, "{\"incident\":{\"id\":");
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final var thrown = assertThrows(CompletionException.class,
        () -> client.createIncident(request()).join());
    final var cause = assertInstanceOf(IncidentIoParseException.class, thrown.getCause());
    assertTrue(cause.getMessage().contains("Failed to adapt 200 response"),
        "the parse failure must name the status it could not adapt");
    assertNotNull(cause.getCause(), "the underlying parse error must be retained");
  }

  @Test
  void aRequestTimeoutOverrideIsUsedWhenTheRequestCarriesOne() throws IOException {
    final var wire = serve(200, SUCCESS_BODY);
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    // timeout() is inherited from Request.Builder and returns that base type, so it is set
    // on the builder rather than mid-chain
    final var builder = CreateIncidentRequest.requestBuilder()
        .name("Wire Incident")
        .summary("Wire Summary")
        .idempotencyKey("idem-2")
        .visibility("public")
        .severityId("sev-1")
        .incidentTypeId("type-1")
        .statusId("status-1")
        .mode("standard");
    builder.timeout(Duration.ofSeconds(20));
    final var withTimeout = builder.build();

    assertNotNull(client.createIncident(withTimeout).join());
    assertEquals(Duration.ofSeconds(20), withTimeout.timeout());
  }

  @Test
  void createIncidentReturnsTheTransportFutureRatherThanNull() throws IOException {
    final var wire = serve(200, SUCCESS_BODY);
    final var client = IncidentIoClient.clientBuilder()
        .endpoint(URI.create(wire.endpoint()))
        .createClient();

    final CompletableFuture<CreateIncidentResponse> future = client.createIncident(request());
    assertNotNull(future);
    assertNotNull(future.join());
  }
}
