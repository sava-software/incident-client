package software.sava.incident.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.webhook.exceptions.WebhookRequestException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

final class WebhookClientTests {

  /// `rawBody` is the request body exactly as it arrived; `body` is its lenient UTF-8 decode.
  private record RecordedRequest(String method, Map<String, List<String>> headers, byte[] rawBody, String body) {
  }

  /// Single-exchange test server: records the request and replies with `statusCode` and
  /// `responseBody` (no body when null).
  private static final class RecordingHandler implements HttpHandler {

    private final int statusCode;
    private final String responseBody;
    private final ConcurrentLinkedQueue<RecordedRequest> requests = new ConcurrentLinkedQueue<>();

    private RecordingHandler(final int statusCode, final String responseBody) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
      try (exchange) {
        final var rawBody = exchange.getRequestBody().readAllBytes();
        requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestHeaders(), rawBody, new String(rawBody, UTF_8)));
        if (responseBody == null) {
          exchange.sendResponseHeaders(statusCode, -1);
        } else {
          final var responseBytes = responseBody.getBytes(UTF_8);
          exchange.sendResponseHeaders(statusCode, responseBytes.length);
          exchange.getResponseBody().write(responseBytes);
        }
      }
    }
  }

  private static HttpServer startServer(final HttpHandler handler) throws IOException {
    final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", handler);
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    return server;
  }

  private static URI endpoint(final HttpServer server) {
    return URI.create("http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + "/hook");
  }

  @Test
  void postSendsBodyAndConfiguredHeaders() throws IOException {
    final var handler = new RecordingHandler(200, "ok");
    final var server = startServer(handler);
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .header("X-Api-Key", "key-1")
          .bearerToken("token-1")
          .createClient();

      final var responseBody = client.post("""
          {"a":1}""").join();

      assertEquals("ok", responseBody);
      final var request = handler.requests.poll();
      assertNotNull(request);
      assertEquals("POST", request.method());
      assertEquals("""
          {"a":1}""", request.body());
      assertEquals(List.of("application/json"), request.headers().get("Content-type"));
      assertEquals(List.of("key-1"), request.headers().get("X-api-key"));
      assertEquals(List.of("Bearer token-1"), request.headers().get("Authorization"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void headerReplacesEarlierValue() throws IOException {
    final var handler = new RecordingHandler(200, "ok");
    final var server = startServer(handler);
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .bearerToken("stale")
          .header("Authorization", "Bearer fresh")
          .createClient();
      client.post("{}").join();
      final var request = handler.requests.poll();
      assertNotNull(request);
      assertEquals(List.of("Bearer fresh"), request.headers().get("Authorization"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void headersComposeWithCustomExtendRequest() throws IOException {
    final var handler = new RecordingHandler(200, "ok");
    final var server = startServer(handler);
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .header("X-One", "1")
          .extendRequest(requestBuilder -> {
            requestBuilder.setHeader("X-Two", "2");
            return requestBuilder;
          })
          .createClient();
      client.post("{}").join();
      final var request = handler.requests.poll();
      assertNotNull(request);
      assertEquals(List.of("1"), request.headers().get("X-one"));
      assertEquals(List.of("2"), request.headers().get("X-two"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void emptyResponseBodyReadsAsEmptyString() throws IOException {
    final var server = startServer(new RecordingHandler(200, null));
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient();
      assertEquals("", client.post("{}").join());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void non2xxFailsWithRequestException() throws IOException {
    final var server = startServer(new RecordingHandler(400, "invalid_payload"));
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient();
      final var ex = assertThrows(CompletionException.class, () -> client.post("{}").join());
      final var cause = assertInstanceOf(WebhookRequestException.class, ex.getCause());
      assertEquals(400L, cause.errorCode());
      assertEquals("invalid_payload", cause.body());
      assertEquals(List.of("invalid_payload"), cause.errors());
      assertFalse(cause.canBeRetried());
      // the endpoint URL is the credential; the message must not carry it
      assertFalse(cause.getMessage().contains("/hook"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void serverErrorsCanBeRetried() throws IOException {
    final var server = startServer(new RecordingHandler(503, "busy"));
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient();
      final var ex = assertThrows(CompletionException.class, () -> client.post("{}").join());
      final var cause = assertInstanceOf(WebhookRequestException.class, ex.getCause());
      assertEquals(503L, cause.errorCode());
      assertTrue(cause.canBeRetried());
    } finally {
      server.stop(0);
    }
  }

  /// 300 is the first non-success status: redirects are not followed as deliveries.
  @Test
  void redirectStatusIsAnError() throws IOException {
    final var server = startServer(new RecordingHandler(300, null));
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient();
      final var ex = assertThrows(CompletionException.class, () -> client.post("{}").join());
      final var cause = assertInstanceOf(WebhookRequestException.class, ex.getCause());
      assertEquals(300L, cause.errorCode());
      assertFalse(cause.canBeRetried());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void toStringCarriesHostButNeverThePath() {
    final var client = WebhookClient.clientBuilder()
        .endpoint("https://hooks.slack.com/services/T000/B000/secret")
        .createClient();
    assertTrue(client.toString().contains("hooks.slack.com"));
    assertFalse(client.toString().contains("secret"));

    final var incidentClient = client.incidentClient(WebhookFormats.GENERIC_JSON);
    assertTrue(incidentClient.toString().contains("GENERIC_JSON"));
    assertFalse(incidentClient.toString().contains("secret"));
  }

  @Test
  void reportIncidentPostsRenderedAlert() throws IOException {
    final var handler = new RecordingHandler(200, "ok\n");
    final var server = startServer(handler);
    try {
      final var incidentClient = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient()
          .incidentClient(WebhookFormats.GENERIC_JSON);

      final var response = incidentClient.reportIncident(IncidentAlert.build()
          .key("dedup-1")
          .summary("summary-1")
          .severity(IncidentSeverity.ERROR)
          .timestamp(ZonedDateTime.of(2026, 7, 26, 1, 2, 3, 0, ZoneOffset.UTC))
          .create()).join();

      assertEquals("dedup-1", response.key());
      assertEquals("ok", response.status());
      assertNull(response.url());
      final var request = handler.requests.poll();
      assertNotNull(request);
      assertEquals("""
          {"summary":"summary-1","severity":"ERROR","key":"dedup-1","timestamp":"2026-07-26T01:02:03Z"}""", request.body());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportIncidentEmptyBodyMapsToDelivered() throws IOException {
    final var server = startServer(new RecordingHandler(200, null));
    try {
      final var incidentClient = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient()
          .incidentClient(WebhookFormats.SLACK_TEXT);
      final var response = incidentClient.reportIncident(IncidentAlert.build()
          .summary("summary-1")
          .severity(IncidentSeverity.INFO)
          .create()).join();
      assertNull(response.key());
      assertEquals("delivered", response.status());
    } finally {
      server.stop(0);
    }
  }

  /// A supplementary character (U+1F6A8, two UTF-16 units) straddling the `sendMessage`
  /// limit: the text sent is the first 4,095 units, and the body on the wire is strict
  /// UTF-8 for exactly that JSON, with no lone surrogate half turned into `?`.
  @Test
  void telegramTruncationKeepsTheBodyStrictUtf8() throws IOException {
    final var handler = new RecordingHandler(200, "{\"ok\":true}");
    final var server = startServer(handler);
    try {
      final var incidentClient = WebhookClient.clientBuilder()
          .endpoint(endpoint(server))
          .createClient()
          .incidentClient(new TelegramTextFormat("chat-1"));
      final var prefix = "[WARNING] ";
      // the pair occupies indices 4,095 and 4,096 of the rendered text
      final var head = "s".repeat(TelegramTextFormat.MAX_TEXT_LENGTH - 1 - prefix.length());
      incidentClient.reportIncident(IncidentAlert.build()
          .summary(head + "\uD83D\uDEA8 after the cut")
          .severity(IncidentSeverity.WARNING)
          .create()).join();

      final var request = handler.requests.poll();
      assertNotNull(request);
      final String decoded;
      try {
        decoded = UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(request.rawBody()))
            .toString();
      } catch (final CharacterCodingException e) {
        throw new AssertionError("request body is not strict UTF-8", e);
      }
      assertEquals("""
          {"chat_id":"chat-1","text":"%s%s"}""".formatted(prefix, head), decoded);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void resolveIsUnsupported() {
    final var incidentClient = WebhookClient.clientBuilder()
        .endpoint("http://127.0.0.1:9/unused")
        .createClient()
        .incidentClient(WebhookFormats.GENERIC_JSON);
    assertFalse(incidentClient.supportsResolve());
    assertEquals(0, incidentClient.maxKeyLength(), "the webhook formats document no key limit: none is declared");
    final var ex = assertThrows(CompletionException.class, () -> incidentClient.resolveIncident("key-1").join());
    assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
  }

  @Test
  void builderRequiresEndpoint() {
    final var ex = assertThrows(NullPointerException.class,
        () -> WebhookClient.clientBuilder().createClient());
    assertEquals("'endpoint' is required.", ex.getMessage());
  }

  @Test
  void adapterDelegatesEndpointAndHttpClient() {
    final var client = WebhookClient.clientBuilder()
        .endpoint("http://127.0.0.1:9/unused")
        .createClient();
    final var incidentClient = client.incidentClient(WebhookFormats.GENERIC_JSON);
    assertEquals(client.endpoint(), incidentClient.endpoint());
    assertSame(client.httpClient(), incidentClient.httpClient());
  }
}
