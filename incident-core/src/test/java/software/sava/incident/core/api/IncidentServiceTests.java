package software.sava.incident.core.api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.hardening.support.JulRecorder;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

final class IncidentServiceTests {

  private static final IncidentAlert ALERT = IncidentAlert.build()
      .key("alert-key")
      .summary("summary")
      .severity(IncidentSeverity.ERROR)
      .create();

  private static final String LOGGER_NAME = IncidentService.class.getPackageName();

  private static final Logger RETRY_LOGGER = Logger.getLogger(LOGGER_NAME);
  private static Level previousLevel;

  @BeforeAll
  static void silenceRetryLogging() {
    // the retry path logs every failure at ERROR with its throwable, so exercising it
    // prints stacktraces out of passing tests. JulRecorder re-enables the logger and
    // detaches parent handlers for the tests that assert on these records.
    previousLevel = RETRY_LOGGER.getLevel();
    RETRY_LOGGER.setLevel(Level.OFF);
  }

  @AfterAll
  static void restoreRetryLogging() {
    RETRY_LOGGER.setLevel(previousLevel);
  }

  private record TestHttpResponse(int statusCode) implements HttpResponse<Object> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    @Override
    public Object body() {
      return null;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return null;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class TestClientException extends RuntimeException implements IncidentClientException {

    private final boolean canBeRetried;
    private final HttpResponse<?> httpResponse;
    private final long errorCode;

    private TestClientException(final boolean canBeRetried) {
      this(canBeRetried, null, 0);
    }

    private TestClientException(final boolean canBeRetried,
                                final HttpResponse<?> httpResponse,
                                final long errorCode) {
      super(canBeRetried ? "retriable" : "fatal");
      this.canBeRetried = canBeRetried;
      this.httpResponse = httpResponse;
      this.errorCode = errorCode;
    }

    @Override
    public boolean canBeRetried() {
      return canBeRetried;
    }

    @Override
    public HttpResponse<?> httpResponse() {
      return httpResponse;
    }

    @Override
    public long errorCode() {
      return errorCode;
    }

    @Override
    public List<String> errors() {
      return List.of();
    }
  }

  private static final class TestCheckedClientException extends Exception implements IncidentClientException {

    private TestCheckedClientException(final Throwable cause) {
      super("fatal-checked", cause);
    }

    @Override
    public boolean canBeRetried() {
      return false;
    }

    @Override
    public HttpResponse<?> httpResponse() {
      return null;
    }

    @Override
    public long errorCode() {
      return 0;
    }

    @Override
    public List<String> errors() {
      return List.of();
    }
  }

  private static final class StubClient implements IncidentClient {

    private final AtomicInteger calls = new AtomicInteger();
    private final List<IncidentAlert> reports = new ArrayList<>();
    private final int failures;
    private final boolean canBeRetried;
    private boolean wrapFailures;
    private boolean supportsResolve = true;
    private CompletableFuture<IncidentResponse> resolveResult;
    private Supplier<Throwable> failureFactory;

    private StubClient(final int failures, final boolean canBeRetried) {
      this.failures = failures;
      this.canBeRetried = canBeRetried;
    }

    private CompletableFuture<IncidentResponse> respond(final String key) {
      if (calls.incrementAndGet() <= failures) {
        final var exception = failureFactory == null ? new TestClientException(canBeRetried) : failureFactory.get();
        return CompletableFuture.failedFuture(wrapFailures ? new CompletionException(exception) : exception);
      }
      return CompletableFuture.completedFuture(IncidentResponse.of(key, "success", null));
    }

    @Override
    public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
      reports.add(alert);
      return respond(alert.key() == null ? "generated" : alert.key());
    }

    @Override
    public boolean supportsResolve() {
      return supportsResolve;
    }

    @Override
    public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
      if (resolveResult != null) {
        calls.incrementAndGet();
        return resolveResult;
      }
      return respond(key);
    }

    @Override
    public URI endpoint() {
      return null;
    }

    @Override
    public HttpClient httpClient() {
      return null;
    }
  }

  @Test
  void missingReportKeyIsGeneratedOnceAndPreservesAlertAcrossRetries() {
    for (final String key : new String[]{null, "", " \t"}) {
      final var alert = IncidentAlert.build()
          .key(key)
          .summary("Database unavailable")
          .details("Connection refused")
          .severity(IncidentSeverity.CRITICAL)
          .source("database-3")
          .timestamp(ZonedDateTime.parse("2026-10-24T12:00:00+02:00[Europe/Zurich]"))
          .customDetail("region", "eu-central")
          .customDetail("attempt", 3)
          .create();
      final var client = new StubClient(2, true);
      final var service = IncidentService.createService(client);

      final var response = service.reportIncident(alert, 2, 0, 0, TimeUnit.MILLISECONDS).join();

      assertEquals(3, client.reports.size());
      final var firstAttempt = client.reports.getFirst();
      assertNotNull(firstAttempt.key());
      assertDoesNotThrow(() -> UUID.fromString(firstAttempt.key()));
      assertEquals(firstAttempt.key(), response.key());
      for (final var attempt : client.reports) {
        assertEquals(firstAttempt.key(), attempt.key());
        assertEquals(alert.summary(), attempt.summary());
        assertEquals(alert.details(), attempt.details());
        assertEquals(alert.severity(), attempt.severity());
        assertEquals(alert.source(), attempt.source());
        assertEquals(alert.timestamp(), attempt.timestamp());
        assertEquals(alert.customDetails(), attempt.customDetails());
      }
      assertEquals(key, alert.key(), "The caller's alert must not be changed");

      final var nextResponse = service.reportIncident(alert, 2, 0, 0, TimeUnit.MILLISECONDS).join();
      assertNotEquals(response.key(), nextResponse.key(), "Separate reports must not deduplicate each other");
    }
  }

  private record CallerAlert(String key,
                             String summary,
                             String details,
                             IncidentSeverity severity,
                             String source,
                             ZonedDateTime timestamp,
                             Map<String, Object> customDetails) implements IncidentAlert {

    @Override
    public String toString() {
      return "CallerAlert[redacted]";
    }
  }

  @Test
  void callerAlertsWithNullCustomDetailsAreDeliveredWithoutCopying() {
    for (final String key : new String[]{null, " ", "explicit-key"}) {
      final var alert = new CallerAlert(key, "summary", "details", IncidentSeverity.ERROR,
          "source", ZonedDateTime.parse("2026-10-25T12:00:00Z"), Collections.singletonMap("trace", null));
      final var client = new StubClient(1, true);
      final var service = IncidentService.createService(client);

      service.reportIncident(alert, 1, 0, 0, TimeUnit.MILLISECONDS).join();

      assertEquals(2, client.reports.size());
      for (final var attempt : client.reports) {
        assertNotNull(attempt.key());
        assertFalse(attempt.key().isBlank());
        assertSame(alert.customDetails(), attempt.customDetails());
        assertTrue(attempt.customDetails().containsKey("trace"));
        assertNull(attempt.customDetails().get("trace"));
      }
      assertSame(client.reports.getFirst(), client.reports.getLast());
      assertEquals(key, alert.key());
    }
  }

  @Test
  void keylessAlertsAreNotRevalidatedByTheService() {
    final var alerts = List.of(
        IncidentAlert.build().severity(IncidentSeverity.ERROR),
        IncidentAlert.build().summary("summary"));
    for (final var alert : alerts) {
      final var client = new StubClient(0, true);
      final var service = IncidentService.createService(client);

      service.reportIncident(alert, 0, 0, 0, TimeUnit.MILLISECONDS).join();

      final var delivered = client.reports.getFirst();
      assertEquals(alert.summary(), delivered.summary());
      assertEquals(alert.severity(), delivered.severity());
      assertNotNull(delivered.key());
      assertNull(alert.key());
    }
  }

  @Test
  void generatedKeyPreservesCallerRedactionInFailureLogs() {
    final var alert = new CallerAlert(null, "summary", "secret detail", IncidentSeverity.ERROR,
        "source", null, Map.of("token", "secret token"));
    final var client = new StubClient(1, true);
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      service.reportIncident(alert, 1, 0, 0, TimeUnit.MILLISECONDS).join();

      assertTrue(recorder.logged("CallerAlert[redacted]"));
      assertFalse(recorder.logged("secret detail"));
      assertFalse(recorder.logged("secret token"));
      assertEquals(alert.toString(), client.reports.getFirst().toString());
    }
  }

  @Test
  void explicitReportKeyAndAlertArePreservedAcrossRetries() {
    final var alert = IncidentAlert.build()
        .key(" incident-key ")
        .summary("Database unavailable")
        .severity(IncidentSeverity.CRITICAL)
        .create();
    final var client = new StubClient(1, true);
    final var service = IncidentService.createService(client);

    final var response = service.reportIncident(alert, 1, 0, 0, TimeUnit.MILLISECONDS).join();

    assertEquals(" incident-key ", response.key());
    assertEquals(2, client.reports.size());
    client.reports.forEach(attempt -> assertSame(alert, attempt));
  }

  @Test
  void succeedsAfterRetriableFailures() {
    final var client = new StubClient(2, true);
    final var service = IncidentService.createService(client);
    assertSame(client, service.client());

    final var response = service.reportIncident(ALERT, 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("alert-key", response.key());
    assertEquals("success", response.status());
    assertEquals(3, client.calls.get());
  }

  @Test
  void doesNotRetryFatalFailures() {
    final var client = new StubClient(Integer.MAX_VALUE, false);
    final var service = IncidentService.createService(client);

    final var future = service.reportIncident(ALERT, 5, 1, 1, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertInstanceOf(TestClientException.class, thrown.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void resolveRetriesUntilSuccess() {
    final var client = new StubClient(1, true);
    final var service = IncidentService.createService(client);

    final var response = service.resolveIncident("key-1", 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("key-1", response.key());
    assertEquals(2, client.calls.get());

    assertNull(service.resolveIncident((String) null, 5, 1, 1, TimeUnit.MILLISECONDS));
  }

  @Test
  void unsupportedResolveDelegatesOnceWithoutSchedulingRetries() {
    final var client = new StubClient(0, true);
    client.supportsResolve = false;
    final var cause = new UnsupportedOperationException("No incident lifecycle");
    client.resolveResult = CompletableFuture.failedFuture(cause);
    final var service = IncidentService.createService(client);
    final var delayCalls = new AtomicInteger();

    // Bound the broken implementation too: a regression must not create an endless retry loop.
    final var result = service.resolveIncident("key-unsupported", attempt -> {
      delayCalls.incrementAndGet();
      return attempt < 2 ? 0 : -1;
    }, TimeUnit.MILLISECONDS);

    assertSame(client.resolveResult, result);
    assertSame(cause, assertThrows(CompletionException.class, result::join).getCause());
    assertEquals(1, client.calls.get());
    assertEquals(0, delayCalls.get());
    assertNull(service.resolveIncident((String) null, attempt -> {
      fail("A null key must not schedule a resolve");
      return 0;
    }, TimeUnit.MILLISECONDS));
    assertEquals(1, client.calls.get());
  }

  @Test
  void unsupportedResolveFromAReportResponseFailsOnTheFirstAttempt() {
    final var client = new StubClient(1, true);
    client.supportsResolve = false;
    final var cause = new UnsupportedOperationException("No incident lifecycle");
    client.failureFactory = () -> cause;
    final var service = IncidentService.createService(client);

    // The stub succeeds on a second call so even the old unlimited overload terminates.
    final var result = service.resolveIncident(IncidentResponse.of("report-key", "ok", null),
        0, 0, TimeUnit.MILLISECONDS);

    assertSame(cause, assertThrows(CompletionException.class, result::join).getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void givesUpAfterMaxRetries() {
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var service = IncidentService.createService(client);

    final var future = service.reportIncident(ALERT, 2, 1, 1, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
  }

  @Test
  void wrappedClientExceptionsAreUnwrapped() {
    // upstream stages may deliver failures wrapped in a CompletionException
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    final var service = IncidentService.createService(client);
    final var response = service.reportIncident(ALERT, 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("success", response.status());
    assertEquals(2, client.calls.get());

    final var fatalClient = new StubClient(Integer.MAX_VALUE, false);
    fatalClient.wrapFailures = true;
    final var fatalFuture = IncidentService.createService(fatalClient)
        .reportIncident(ALERT, 5, 1, 1, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, fatalFuture::join);
    assertEquals(1, fatalClient.calls.get());
  }

  @Test
  void convenienceOverloads() {
    final var client = new StubClient(0, true);
    final var service = IncidentService.createService(client);

    assertEquals("k1", service.resolveIncident(IncidentResponse.of("k1", "s", null), 1, 1, TimeUnit.MILLISECONDS)
        .join().key());
    assertNull(service.resolveIncident((IncidentResponse) null, 1, 1, TimeUnit.MILLISECONDS));
    assertEquals("alert-key", service.reportIncident(ALERT, 1, 1, TimeUnit.MILLISECONDS).join().key());
    assertEquals("alert-key", service.reportIncident(ALERT, java.time.Duration.ofMillis(100), 1, 1, TimeUnit.MILLISECONDS)
        .join().key());
    assertEquals("k2", service.resolveIncident("k2", 1, 1, TimeUnit.MILLISECONDS).join().key());
    assertEquals("k3", service.resolveIncident("k3", java.time.Duration.ofMillis(100), 1, 1, TimeUnit.MILLISECONDS)
        .join().key());
    assertEquals(5, client.calls.get());
  }

  @Test
  void retryDelayFnGivesUpNegative() {
    assertTrue(IncidentService.createRetryDelayFn(2, 1, 10).applyAsLong(3) < 0);
    assertEquals(2, IncidentService.createRetryDelayFn(2, 1, 10).applyAsLong(2));
    assertEquals(10, IncidentService.createRetryDelayFn(1, 10).applyAsLong(100));
  }

  @Test
  void retryDelayFnScalesByStepDelay() {
    // below the max cap the delay is numFailures * stepDelay, not a division or offset
    assertEquals(15, IncidentService.createRetryDelayFn(5, 1_000).applyAsLong(3));
    assertEquals(15, IncidentService.createRetryDelayFn(10, 5, 1_000).applyAsLong(3));
  }

  @Test
  void giveUpAfterDerivesMaxRetriesFromStepDelay() {
    // 10ms give-up / 5ms step -> 2 retries; maxDelay 0 keeps every retry immediate
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var future = IncidentService.createService(client)
        .reportIncident(ALERT, Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());

    final var resolveClient = new StubClient(Integer.MAX_VALUE, true);
    final var resolveFuture = IncidentService.createService(resolveClient)
        .resolveIncident("key-giveup", Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, resolveFuture::join);
    assertEquals(3, resolveClient.calls.get());
  }

  @Test
  void transportFailuresWithoutClientCauseAreRetried() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> new RuntimeException("transport");
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var response = service.reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertEquals("success", response.status());
      assertTrue(recorder.logged("Failure Count: 1"));
      assertTrue(recorder.logged("to report incident"));
      assertFalse(recorder.logged("Http Error Code"));
    }
    assertEquals(2, client.calls.get());
  }

  @Test
  void clientFailureLogsHttpAndServiceErrorCodes() {
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    client.failureFactory = () -> new TestClientException(true, new TestHttpResponse(503), 42);
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var response = service.reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertEquals("success", response.status());
      assertTrue(recorder.logged("Http Error Code: 503"));
      assertTrue(recorder.logged("Service Error Code: 42"));
      assertTrue(recorder.logged("to report incident"));
    }
    assertEquals(2, client.calls.get());
  }

  @Test
  void unknownClientCodesLogPlaceholders() {
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      service.reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertTrue(recorder.logged("Http Error Code: ?"));
      assertTrue(recorder.logged("Service Error Code: ?"));
    }
  }

  @Test
  void resolveFailureLogsTheKey() {
    final var client = new StubClient(1, true);
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var response = service.resolveIncident("key-log", 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertEquals("key-log", response.key());
      assertTrue(recorder.logged("to resolve incident with key 'key-log'"));
    }
  }

  @Test
  void fatalCheckedFailurePropagatesItsRuntimeCause() {
    final var cause = new IllegalStateException("cause-a");
    final var client = new StubClient(Integer.MAX_VALUE, false);
    client.failureFactory = () -> new TestCheckedClientException(cause);
    final var future = IncidentService.createService(client)
        .reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertSame(cause, thrown.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void fatalCheckedFailureWithoutRuntimeCauseIsWrapped() {
    final var cause = new Exception("cause-b");
    final var client = new StubClient(Integer.MAX_VALUE, false);
    client.failureFactory = () -> new TestCheckedClientException(cause);
    final var future = IncidentService.createService(client)
        .reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    final var wrapped = assertInstanceOf(RuntimeException.class, thrown.getCause());
    assertSame(cause, wrapped.getCause());
    assertEquals(1, client.calls.get());
  }
}
