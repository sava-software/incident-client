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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
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
    private int maxKeyLength;
    private final List<String> resolvedKeys = new ArrayList<>();
    private CompletableFuture<IncidentResponse> resolveResult;
    private Supplier<Throwable> failureFactory;
    private RuntimeException thrownSynchronously;

    private StubClient(final int failures, final boolean canBeRetried) {
      this.failures = failures;
      this.canBeRetried = canBeRetried;
    }

    private CompletableFuture<IncidentResponse> respond(final String key) {
      if (calls.incrementAndGet() <= failures) {
        final var exception = failureFactory == null ? new TestClientException(canBeRetried) : failureFactory.get();
        return CompletableFuture.failedFuture(wrapFailures ? new CompletionException(exception) : exception);
      }
      if (thrownSynchronously != null) {
        throw thrownSynchronously;
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
    public int maxKeyLength() {
      return maxKeyLength;
    }

    @Override
    public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
      resolvedKeys.add(key);
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

  private static IncidentAlert keyed(final String key) {
    return IncidentAlert.build().key(key).summary("Scope mappings refused").severity(IncidentSeverity.WARNING).create();
  }

  /// A key past the client's limit reaches the provider shortened, the same way on every report
  /// and on the resolve by the caller's long key, while the caller's alert keeps its key.
  @Test
  void aKeyPastTheClientsLimitIsShortenedTheSameWayOnReportAndResolve() {
    final var client = new StubClient(1, true);
    client.maxKeyLength = 255;
    final var service = IncidentService.createService(client);
    final var longKey = "Scope Mappings Refused:" + "k".repeat(300);
    final var alert = keyed(longKey);

    final var response = service.reportIncident(alert, 2, 0, 0, TimeUnit.MILLISECONDS).join();

    assertEquals(2, client.reports.size(), "one failure, one success");
    final var reported = client.reports.getFirst().key();
    assertEquals(255, reported.length());
    assertTrue(reported.startsWith("Scope Mappings Refused:kkk"), reported);
    assertEquals(AlertKeys.bound(longKey, 255), reported);
    assertEquals(reported, client.reports.get(1).key(), "the shortened key is shared by the attempts");
    assertEquals(reported, response.key());
    assertEquals(alert.summary(), client.reports.getFirst().summary(), "the forwarding view carries the alert");
    assertEquals(longKey, alert.key(), "The caller's alert must not be changed");

    service.resolveIncident(longKey, 1, 0, 0, TimeUnit.MILLISECONDS).join();
    assertEquals(reported, client.resolvedKeys.getLast(), "a resolve by the long key finds the provider's");
    service.resolveIncident(response, 0, 0, TimeUnit.MILLISECONDS).join();
    assertEquals(reported, client.resolvedKeys.getLast(), "a resolve by the response's key is unchanged");
  }

  /// A client declaring a limit under the 64-character digest, which the shortening refuses, is
  /// refused when the service is created rather than at its first report; 64, and no limit, are
  /// taken.
  @Test
  void aLimitUnderTheDigestIsRefused() {
    final var client = new StubClient(0, true);
    client.maxKeyLength = 63;
    final var refusal = assertThrows(IllegalArgumentException.class, () -> IncidentService.createService(client));
    assertEquals("A key limit of 63 is under the 64-character digest: declare 64 or more, or none.", refusal.getMessage());
    client.maxKeyLength = 64;
    assertNotNull(IncidentService.createService(client));
    client.maxKeyLength = 0;
    assertNotNull(IncidentService.createService(client));
  }

  /// A client that cannot resolve still receives the key shortened: the one delegation it gets
  /// names the key the provider knows.
  @Test
  void anUnsupportedResolveStillSendsTheShortenedKey() {
    final var client = new StubClient(0, true);
    client.supportsResolve = false;
    client.maxKeyLength = 255;
    final var longKey = "Scope Mappings Refused:" + "k".repeat(300);
    IncidentService.createService(client).resolveIncident(longKey, 5, 0, 0, TimeUnit.MILLISECONDS);
    assertEquals(List.of(AlertKeys.bound(longKey, 255)), client.resolvedKeys);
  }

  /// A failed report of a key sent as itself is logged under the alert alone.
  @Test
  void aPlainReportFailureLogsTheAlertAlone() {
    final var client = new StubClient(1, true);
    client.maxKeyLength = 255;
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      IncidentService.createService(client).reportIncident(keyed("plain"), 2, 0, 0, TimeUnit.MILLISECONDS).join();
      assertTrue(recorder.logged("to report incident:"));
      assertFalse(recorder.logged("sent with key"), "a key sent as itself is logged once");
    }
  }

  /// A failed report of a shortened key is logged under the alert and the key sent, as the
  /// resolve is.
  @Test
  void aShortenedReportFailureLogsTheKeySent() {
    final var client = new StubClient(1, true);
    client.maxKeyLength = 255;
    final var longKey = "Scope Mappings Refused:" + "k".repeat(300);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      IncidentService.createService(client).reportIncident(keyed(longKey), 2, 0, 0, TimeUnit.MILLISECONDS).join();
      assertTrue(recorder.logged("to report incident, sent with key '" + AlertKeys.bound(longKey, 255) + "'"));
    }
  }

  /// A key within the limit, and any key for a client that declares none, reaches the provider as
  /// the caller's own alert instance.
  @Test
  void aKeyWithinTheClientsLimitKeepsTheCallersAlert() {
    final var bounded = new StubClient(0, true);
    bounded.maxKeyLength = 255;
    final var atTheLimit = keyed("k".repeat(255));
    IncidentService.createService(bounded).reportIncident(atTheLimit, 0, 0, TimeUnit.MILLISECONDS).join();
    assertSame(atTheLimit, bounded.reports.getFirst());

    final var unbounded = new StubClient(0, true);
    final var longKey = keyed("k".repeat(1_000));
    IncidentService.createService(unbounded).reportIncident(longKey, 0, 0, TimeUnit.MILLISECONDS).join();
    assertSame(longKey, unbounded.reports.getFirst());
    IncidentService.createService(unbounded).resolveIncident(longKey.key(), 0, 0, TimeUnit.MILLISECONDS).join();
    assertEquals(longKey.key(), unbounded.resolvedKeys.getLast());
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

  /// A failed report of a generated key is logged under the alert and the key sent, as a
  /// shortened one is, since the alert does not carry it; before the limit it was logged under
  /// the alert alone.
  @Test
  void aGeneratedKeyFailureLogsTheKeySent() {
    final var client = new StubClient(1, true);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      IncidentService.createService(client).reportIncident(keyed(null), 2, 0, 0, TimeUnit.MILLISECONDS).join();
      final var sent = client.reports.getFirst().key();
      assertDoesNotThrow(() -> UUID.fromString(sent));
      assertTrue(recorder.logged("to report incident, sent with key '" + sent + "':"));
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
    final var failures = new ArrayList<Throwable>();
    final var client = new StubClient(Integer.MAX_VALUE, true);
    client.failureFactory = keptIn(failures, () -> new TestClientException(true));
    final var service = IncidentService.createService(client);

    final var future = service.reportIncident(ALERT, 2, 1, 1, TimeUnit.MILLISECONDS);
    // join first: the retries run on the delayed executor's thread
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
    assertSame(failures.getLast(), thrown.getCause());
  }

  /// Each failure the factory makes, kept in `made` in the order the attempts met them.
  private static Supplier<Throwable> keptIn(final List<Throwable> made, final Supplier<Throwable> factory) {
    return () -> {
      final var failure = factory.get();
      made.add(failure);
      return failure;
    };
  }

  /// A max-retries call against a client that always fails with a retryable error ends, after
  /// exactly `maxRetries + 1` attempts, with the last attempt's failure itself, on report and on
  /// resolve.
  @Test
  void runningOutOfRetriesFailsWithTheLastAttemptsFailure() {
    final List<java.util.function.Function<IncidentService, CompletableFuture<IncidentResponse>>> calls = List.of(
        service -> service.reportIncident(ALERT, 3, 0, 0, MS),
        service -> service.resolveIncident("key-out", 3, 0, 0, MS));
    for (int i = 0; i < calls.size(); ++i) {
      final var failures = new ArrayList<Throwable>();
      final var client = new StubClient(Integer.MAX_VALUE, true);
      client.failureFactory = keptIn(failures, () -> new TestClientException(true));
      final var delays = new RecordingDelays();
      final var future = calls.get(i).apply(new IncidentServiceVal(client, Duration.ofMinutes(1), delays));
      final var thrown = assertThrows(CompletionException.class, future::join, "call " + i);
      assertEquals(4, client.calls.get(), "call " + i);
      assertEquals(4, failures.size(), "call " + i);
      assertSame(failures.getLast(), thrown.getCause(), "call " + i);
      assertNotSame(failures.getFirst(), thrown.getCause(), "call " + i);
      assertEquals(List.of(), delays.requests, "call " + i);
    }
  }

  /// Giving up surfaces the last failure in the shapes a non-retryable failure has: a runtime
  /// exception delivered bare as the cause, one an upstream stage wrapped in a
  /// [CompletionException] as that wrapper's cause, a checked one's runtime cause, and a checked
  /// one whose cause is checked wrapped in a [RuntimeException]; on report and on resolve.
  @Test
  void aGiveUpSurfacesTheLastFailureInEveryShape() {
    final List<java.util.function.Function<IncidentService, CompletableFuture<IncidentResponse>>> calls = List.of(
        service -> service.reportIncident(ALERT, 1, 0, 0, MS),
        service -> service.resolveIncident("key-shape", 1, 0, 0, MS));
    for (int i = 0; i < calls.size(); ++i) {
      final var call = calls.get(i);

      final var bare = new ArrayList<Throwable>();
      final var bareClient = new StubClient(Integer.MAX_VALUE, true);
      bareClient.failureFactory = keptIn(bare, () -> new TestClientException(true));
      final var bareThrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(bareClient))::join);
      assertSame(bare.getLast(), bareThrown.getCause(), "call " + i);
      assertEquals(2, bareClient.calls.get(), "call " + i);

      final var wrapped = new ArrayList<Throwable>();
      final var wrappedClient = new StubClient(Integer.MAX_VALUE, true);
      wrappedClient.wrapFailures = true;
      wrappedClient.failureFactory = keptIn(wrapped, () -> new TestClientException(true));
      final var wrappedThrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(wrappedClient))::join);
      assertSame(wrapped.getLast(), wrappedThrown.getCause(), "call " + i);
      assertEquals(2, wrappedClient.calls.get(), "call " + i);

      final var runtimeCauses = new ArrayList<Throwable>();
      final var checkedClient = new StubClient(Integer.MAX_VALUE, true);
      checkedClient.failureFactory = () -> {
        final var cause = new IllegalStateException("cause-" + runtimeCauses.size());
        runtimeCauses.add(cause);
        return new WaitingCheckedClientException(cause, null);
      };
      final var checkedThrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(checkedClient))::join);
      assertSame(runtimeCauses.getLast(), checkedThrown.getCause(), "call " + i);
      assertEquals(2, checkedClient.calls.get(), "call " + i);

      final var checkedCauses = new ArrayList<Throwable>();
      final var bareCheckedClient = new StubClient(Integer.MAX_VALUE, true);
      bareCheckedClient.failureFactory = () -> {
        final var cause = new Exception("checked-" + checkedCauses.size());
        checkedCauses.add(cause);
        return new WaitingCheckedClientException(cause, null);
      };
      final var bareCheckedThrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(bareCheckedClient))::join);
      final var runtime = assertInstanceOf(RuntimeException.class, bareCheckedThrown.getCause(), "call " + i);
      assertSame(checkedCauses.getLast(), runtime.getCause(), "call " + i);
      assertEquals(2, bareCheckedClient.calls.get(), "call " + i);
    }
  }

  /// A transport failure, no [IncidentClientException] anywhere, is retried and on giving up
  /// surfaces as itself; a checked one an upstream stage wrapped in a [CompletionException]
  /// surfaces as that wrapper, the transport failure its cause.
  @Test
  void aTransportFailureSurfacesAsItselfOnAGiveUp() {
    final List<java.util.function.Function<IncidentService, CompletableFuture<IncidentResponse>>> calls = List.of(
        service -> service.reportIncident(ALERT, 2, 0, 0, MS),
        service -> service.resolveIncident("key-transport", 2, 0, 0, MS));
    for (int i = 0; i < calls.size(); ++i) {
      final var call = calls.get(i);

      final var transport = new ArrayList<Throwable>();
      final var client = new StubClient(Integer.MAX_VALUE, true);
      client.failureFactory = keptIn(transport, () -> new RuntimeException("transport-" + transport.size()));
      final var thrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(client))::join);
      assertSame(transport.getLast(), thrown.getCause(), "call " + i);
      assertEquals(3, client.calls.get(), "call " + i);

      final var resets = new ArrayList<Throwable>();
      final var wrappedClient = new StubClient(Integer.MAX_VALUE, true);
      wrappedClient.wrapFailures = true;
      wrappedClient.failureFactory = keptIn(resets, () -> new java.io.IOException("connection reset-" + resets.size()));
      final var wrappedThrown = assertThrows(CompletionException.class, call.apply(IncidentService.createService(wrappedClient))::join);
      assertSame(resets.getLast(), wrappedThrown.getCause(), "call " + i);
      assertEquals(3, wrappedClient.calls.get(), "call " + i);
    }
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
    final var failures = new ArrayList<Throwable>();
    final var client = new StubClient(Integer.MAX_VALUE, true);
    client.failureFactory = keptIn(failures, () -> new TestClientException(true));
    final var future = IncidentService.createService(client)
        .reportIncident(ALERT, Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
    assertSame(failures.getLast(), thrown.getCause());

    final var resolveFailures = new ArrayList<Throwable>();
    final var resolveClient = new StubClient(Integer.MAX_VALUE, true);
    resolveClient.failureFactory = keptIn(resolveFailures, () -> new TestClientException(true));
    final var resolveFuture = IncidentService.createService(resolveClient)
        .resolveIncident("key-giveup", Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    final var resolveThrown = assertThrows(CompletionException.class, resolveFuture::join);
    assertEquals(3, resolveClient.calls.get());
    assertSame(resolveFailures.getLast(), resolveThrown.getCause());
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
  void aWrappedTransportFailureIsRetried() {
    // an upstream stage delivers a transport failure as the cause of a CompletionException:
    // neither the failure nor its cause is a client exception, so no server wait is read from
    // either, and the call is retried like any other transport failure
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    client.failureFactory = () -> new java.io.IOException("connection reset");
    final var service = IncidentService.createService(client);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var response = service.reportIncident(ALERT, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertEquals("success", response.status());
      assertTrue(recorder.logged("Failure Count: 1"));
      assertFalse(recorder.logged("Retry-After"));
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
      assertTrue(recorder.logged("to resolve incident with key 'key-log'."));
      assertFalse(recorder.logged("sent as"), "a key sent as itself is logged once");
    }
  }

  /// A resolve by a key the provider knows shortened is logged under both, so the caller's key
  /// finds the line.
  @Test
  void aShortenedResolveFailureLogsTheCallersKeyAndTheProviders() {
    final var client = new StubClient(1, true);
    client.maxKeyLength = 255;
    final var service = IncidentService.createService(client);
    final var longKey = "Scope Mappings Refused:" + "k".repeat(300);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var response = service.resolveIncident(longKey, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertEquals(AlertKeys.bound(longKey, 255), response.key());
      assertTrue(recorder.logged("to resolve incident with key '" + longKey + "', sent as '" + AlertKeys.bound(longKey, 255) + "'"));
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

  // ---- a provider's Retry-After ----

  /// A response carrying `Retry-After: <retryAfter>`, so the interface's default reads it.
  private record RetryAfterHttpResponse(int statusCode, String retryAfter) implements HttpResponse<Object> {

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of("Retry-After", List.of(retryAfter)), (_, _) -> true);
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<Object>> previousResponse() {
      return Optional.empty();
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

  /// A provider exception that states its wait itself, overriding `retryAfter()`.
  private static final class WaitingClientException extends RuntimeException implements IncidentClientException {

    private final boolean canBeRetried;
    private final Supplier<Duration> retryAfter;

    private WaitingClientException(final boolean canBeRetried, final Supplier<Duration> retryAfter) {
      super("waiting");
      this.canBeRetried = canBeRetried;
      this.retryAfter = retryAfter;
    }

    @Override
    public Duration retryAfter() {
      return retryAfter.get();
    }

    @Override
    public boolean canBeRetried() {
      return canBeRetried;
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

  /// A retryable checked provider exception that states a wait.
  private static final class WaitingCheckedClientException extends Exception implements IncidentClientException {

    private final Duration retryAfter;

    private WaitingCheckedClientException(final Throwable cause, final Duration retryAfter) {
      super("retriable-checked", cause);
      this.retryAfter = retryAfter;
    }

    @Override
    public Duration retryAfter() {
      return retryAfter;
    }

    @Override
    public boolean canBeRetried() {
      return true;
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

  private record Delay(long delay, TimeUnit unit) {
  }

  /// The delayed-executor seam: records each request when work is submitted to it, then runs
  /// the work inline, so nothing waits.
  private static final class RecordingDelays implements IncidentServiceVal.DelayedExecutorFactory {

    private final List<Delay> requests = new ArrayList<>();
    private final List<String> events;

    private RecordingDelays() {
      this(new ArrayList<>());
    }

    private RecordingDelays(final List<String> events) {
      this.events = events;
    }

    @Override
    public Executor delayedExecutor(final long delay, final TimeUnit unit) {
      return task -> {
        requests.add(new Delay(delay, unit));
        events.add("delay " + delay + ' ' + unit);
        task.run();
      };
    }
  }

  private static final TimeUnit MS = TimeUnit.MILLISECONDS;

  private static TestClientException rateLimited(final String retryAfter) {
    return new TestClientException(true, new RetryAfterHttpResponse(429, retryAfter), 0);
  }

  private static Supplier<Throwable> inTurn(final Throwable... failures) {
    final var next = new AtomicInteger();
    return () -> failures[next.getAndIncrement()];
  }

  private static List<Delay> ms(final long... delays) {
    final var list = new ArrayList<Delay>();
    for (final long delay : delays) {
      list.add(new Delay(delay, MS));
    }
    return list;
  }

  private static LongUnaryOperator constant(final long delay) {
    return _ -> delay;
  }

  /// (a) The canonical sequence: a 30 s wait after attempt 0 is served in full, the step delay
  /// of attempt 1 as today, and the caller's function sees each attempt number once, in order.
  @Test
  void aProvidersWaitIsServedBeforeTheNextAttempt() {
    final var client = new StubClient(2, true);
    client.failureFactory = inTurn(rateLimited("30"), new TestClientException(true));
    final var delays = new RecordingDelays();
    final var seen = new ArrayList<Long>();
    final var fn = IncidentService.createRetryDelayFn(10, 1_000);

    final var response = new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
        .reportIncident(ALERT, n -> {
          seen.add(n);
          return fn.applyAsLong(n);
        }, MS).join();

    assertEquals("success", response.status());
    assertEquals(3, client.calls.get());
    assertEquals(ms(30_000, 10), delays.requests);
    assertEquals(List.of(0L, 1L, 2L), seen);
  }

  /// (b) A wait the attempt's own delay already covers adds nothing; one millisecond more than
  /// it adds that millisecond.
  @Test
  void aWaitTheStepDelayCoversAddsNoRequest() {
    for (final long d : new long[]{40_000, 30_000}) {
      final var client = new StubClient(1, true);
      client.failureFactory = () -> rateLimited("30");
      final var delays = new RecordingDelays();
      new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .reportIncident(ALERT, n -> n == 0 ? d : 0, MS).join();
      assertEquals(ms(d), delays.requests, "d = " + d);
      assertEquals(2, client.calls.get());
    }
    final var client = new StubClient(1, true);
    client.failureFactory = () -> rateLimited("30");
    final var delays = new RecordingDelays();
    new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
        .reportIncident(ALERT, n -> n == 0 ? 29_999 : 0, MS).join();
    assertEquals(ms(29_999, 1), delays.requests);
  }

  /// (c) (d) No header, or `Retry-After: 0`, is no server wait: the next attempt is immediate.
  @Test
  void anAbsentOrZeroWaitIsNoWait() {
    for (final var failure : List.<Supplier<Throwable>>of(
        () -> new TestClientException(true),
        () -> new TestClientException(true, new TestHttpResponse(429), 0),
        () -> rateLimited("0"),
        () -> new WaitingClientException(true, () -> null),
        () -> new WaitingClientException(true, () -> Duration.ZERO),
        () -> new WaitingClientException(true, () -> Duration.ofSeconds(-5)))) {
      final var client = new StubClient(1, true);
      client.failureFactory = failure;
      final var delays = new RecordingDelays();
      final var service = new IncidentServiceVal(client, Duration.ZERO, delays);
      try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
        assertEquals("success", service.reportIncident(ALERT, constant(0), MS).join().status());
        assertFalse(recorder.logged("Retry-After"));
      }
      assertEquals(List.of(), delays.requests);
      assertEquals(2, client.calls.get());
    }
  }

  /// (e) The wait is rounded up to the call's unit, never down.
  @Test
  void aWaitIsRoundedUpToTheCallsUnit() {
    final var cases = Map.of("1", 1L, "60", 1L, "61", 2L);
    cases.forEach((header, minutes) -> {
      final var client = new StubClient(1, true);
      client.failureFactory = () -> rateLimited(header);
      final var delays = new RecordingDelays();
      new IncidentServiceVal(client, Duration.ofMinutes(5), delays)
          .reportIncident(ALERT, constant(0), TimeUnit.MINUTES).join();
      assertEquals(List.of(new Delay(minutes, TimeUnit.MINUTES)), delays.requests, header);
    });
  }

  @Test
  void roundUpSaturatesAndNeverThrows() {
    assertEquals(2, IncidentServiceVal.roundUp(Duration.ofMillis(1_500), TimeUnit.SECONDS));
    assertEquals(2, IncidentServiceVal.roundUp(Duration.ofSeconds(2), TimeUnit.SECONDS));
    assertEquals(1, IncidentServiceVal.roundUp(Duration.ofNanos(1), TimeUnit.DAYS));
    assertEquals(Long.MAX_VALUE, IncidentServiceVal.roundUp(Duration.ofSeconds(Long.MAX_VALUE), TimeUnit.NANOSECONDS));
    assertEquals(Long.MAX_VALUE, IncidentServiceVal.roundUp(Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), TimeUnit.MILLISECONDS));
    assertEquals(Long.MAX_VALUE / 86_400 + 1, IncidentServiceVal.roundUp(Duration.ofSeconds(Long.MAX_VALUE), TimeUnit.DAYS));
  }

  /// (f) A wait carried by the failure's cause, as an upstream stage wraps it, is served too.
  @Test
  void aWaitCarriedByAWrappedCauseIsServed() {
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    client.failureFactory = () -> rateLimited("30");
    final var delays = new RecordingDelays();
    new IncidentServiceVal(client, Duration.ofMinutes(1), delays).reportIncident(ALERT, constant(0), MS).join();
    assertEquals(ms(30_000), delays.requests);
    assertEquals(2, client.calls.get());
  }

  /// (g) A retry-delay function that gives up wins: no wait is served, whether or not it is
  /// within the limit, and the call ends with the failed attempt's error.
  @Test
  void aGiveUpServesNoWait() {
    for (final var header : List.of("30", "3600")) {
      final var failure = rateLimited(header);
      final var client = new StubClient(Integer.MAX_VALUE, true);
      client.failureFactory = () -> failure;
      final var delays = new RecordingDelays();
      final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .reportIncident(ALERT, n -> n == 0 ? 0 : -1, MS);
      final var thrown = assertThrows(CompletionException.class, future::join);
      assertSame(failure, thrown.getCause(), header);
      assertEquals(List.of(), delays.requests);
      assertEquals(1, client.calls.get());
    }
  }

  /// (h) With no server wait, today's timeline: each attempt starts as soon as the previous
  /// handler runs, the handler of attempt n runs after exactly fn(n), and a zero delay asks
  /// the delayed executor for nothing. The failure log lines are today's, in order.
  @Test
  void withoutAServerWaitTheTimelineIsTodays() {
    final var events = new ArrayList<String>();
    final var values = new long[]{0, 7, 3, -1};
    final var attempts = new AtomicInteger();
    final var failures = new ArrayList<TestClientException>();
    final var client = new StubClient(Integer.MAX_VALUE, true);
    client.failureFactory = () -> {
      events.add("attempt " + attempts.getAndIncrement());
      final var failure = new TestClientException(true);
      failures.add(failure);
      return failure;
    };
    final var delays = new RecordingDelays(events);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .resolveIncident("key-h", n -> {
            events.add("fn " + n);
            return values[(int) n];
          }, MS);
      final var thrown = assertThrows(CompletionException.class, future::join);
      assertEquals(3, failures.size());
      assertSame(failures.getLast(), thrown.getCause());
      assertEquals(List.of(
          "Failure Count: 1, Last Delay: 0 MILLISECONDS, to resolve incident with key 'key-h'.",
          "Failure Count: 2, Last Delay: 7 MILLISECONDS, to resolve incident with key 'key-h'.",
          "Failure Count: 3, Last Delay: 3 MILLISECONDS, to resolve incident with key 'key-h'."
      ), recorder.messages());
    }
    assertEquals(List.of(
        "fn 0", "attempt 0",
        "fn 1", "attempt 1", "delay 7 MILLISECONDS",
        "fn 2", "attempt 2", "delay 3 MILLISECONDS",
        "fn 3"
    ), events);
    assertEquals(ms(7, 3), delays.requests);
  }

  /// (i) A wait equal to the limit is served; one over it, by a second or a nanosecond, ends
  /// the call at once with the provider's exception, no further attempt and no wait.
  @Test
  void aWaitOverTheLimitFailsAtOnce() {
    final var limit = Duration.ofSeconds(30);

    final var atLimit = new StubClient(1, true);
    atLimit.failureFactory = () -> rateLimited("30");
    final var servedDelays = new RecordingDelays();
    assertEquals("success", new IncidentServiceVal(atLimit, limit, servedDelays)
        .reportIncident(ALERT, constant(5), MS).join().status());
    assertEquals(ms(5, 29_995), servedDelays.requests);
    assertEquals(2, atLimit.calls.get());

    final var atLimitExactly = new StubClient(1, true);
    atLimitExactly.failureFactory = () -> new WaitingClientException(true, () -> limit);
    final var exactDelays = new RecordingDelays();
    new IncidentServiceVal(atLimitExactly, limit, exactDelays).reportIncident(ALERT, constant(0), MS).join();
    assertEquals(ms(30_000), exactDelays.requests);

    for (final var over : List.<Supplier<RuntimeException>>of(
        () -> rateLimited("31"),
        () -> new WaitingClientException(true, () -> limit.plusNanos(1)))) {
      final var failure = over.get();
      final var client = new StubClient(Integer.MAX_VALUE, true);
      client.failureFactory = () -> failure;
      final var delays = new RecordingDelays();
      final var future = new IncidentServiceVal(client, limit, delays).reportIncident(ALERT, constant(5), MS);
      assertSame(failure, assertThrows(CompletionException.class, future::join).getCause());
      assertEquals(ms(5), delays.requests, "only the failed attempt's own delay");
      assertEquals(1, client.calls.get());
    }
  }

  /// (i) The limit holds through every overload, whatever the retry count, and on resolve.
  @Test
  void theLimitHoldsThroughEveryOverload() {
    final List<java.util.function.Function<IncidentService, CompletableFuture<IncidentResponse>>> calls = List.of(
        service -> service.reportIncident(ALERT, Integer.MAX_VALUE, 1, 1, MS),
        service -> service.reportIncident(ALERT, constant(0), MS),
        service -> service.reportIncident(ALERT, 1, 1, MS),
        service -> service.reportIncident(ALERT, Duration.ofDays(365), 1, 1, MS),
        service -> service.resolveIncident("key-i", constant(0), MS),
        service -> service.resolveIncident("key-i", Integer.MAX_VALUE, 1, 1, MS),
        service -> service.resolveIncident("key-i", Duration.ofDays(365), 1, 1, MS),
        service -> service.resolveIncident(IncidentResponse.of("key-i", "ok", null), 1, 1, MS));
    for (final var call : calls) {
      final var failure = rateLimited("31");
      final var client = new StubClient(Integer.MAX_VALUE, true);
      client.failureFactory = () -> failure;
      final var delays = new RecordingDelays();
      final var future = call.apply(new IncidentServiceVal(client, Duration.ofSeconds(30), delays));
      assertSame(failure, assertThrows(CompletionException.class, future::join).getCause());
      assertEquals(List.of(), delays.requests);
      assertEquals(1, client.calls.get());
    }
  }

  /// (j) The default limit is one minute: 60 s is served, 61 s refused.
  @Test
  void theDefaultLimitIsOneMinute() {
    assertEquals(Duration.ofMinutes(1), IncidentService.DEFAULT_MAX_RETRY_AFTER);
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var byDefault = IncidentService.createService(client);
    assertEquals(IncidentService.createService(client, Duration.ofMinutes(1)), byDefault);
    assertEquals(Duration.ofMinutes(1), ((IncidentServiceVal) byDefault).maxRetryAfter());

    // refused through the public factory itself: no delayed executor is reached
    final var refused = rateLimited("61");
    client.failureFactory = () -> refused;
    assertSame(refused, assertThrows(CompletionException.class,
        () -> byDefault.reportIncident(ALERT, constant(0), MS).join()).getCause());
    assertEquals(1, client.calls.get());

    final var served = new StubClient(1, true);
    served.failureFactory = () -> rateLimited("60");
    final var delays = new RecordingDelays();
    new IncidentServiceVal(served, IncidentService.DEFAULT_MAX_RETRY_AFTER, delays)
        .reportIncident(ALERT, constant(0), MS).join();
    assertEquals(ms(60_000), delays.requests);
  }

  /// (j) A zero limit refuses every wait and still retries a failure without one; a null or
  /// negative limit is refused.
  @Test
  void aZeroLimitRefusesEveryWait() {
    final var refused = rateLimited("1");
    final var client = new StubClient(Integer.MAX_VALUE, true);
    client.failureFactory = () -> refused;
    final var future = IncidentService.createService(client, Duration.ZERO).reportIncident(ALERT, constant(0), MS);
    assertSame(refused, assertThrows(CompletionException.class, future::join).getCause());
    assertEquals(1, client.calls.get());

    final var plain = new StubClient(1, true);
    assertEquals("success", IncidentService.createService(plain, Duration.ZERO)
        .reportIncident(ALERT, constant(0), MS).join().status());
    assertEquals(2, plain.calls.get());

    final var any = new StubClient(0, true);
    assertThrows(NullPointerException.class, () -> IncidentService.createService(any, null));
    final var negative = assertThrows(IllegalArgumentException.class,
        () -> IncidentService.createService(any, Duration.ofNanos(-1)));
    assertEquals("maxRetryAfter must not be negative: PT-0.000000001S", negative.getMessage());
  }

  /// (k) A refusal surfaces the provider's failure as a non-retryable one would: a runtime
  /// exception as the cause, the wrapper an upstream stage added as is, and a checked one's
  /// runtime cause.
  @Test
  void aRefusalSurfacesTheProvidersFailure() {
    final var direct = rateLimited("61");
    final var directClient = new StubClient(Integer.MAX_VALUE, true);
    directClient.failureFactory = () -> direct;
    assertSame(direct, assertThrows(CompletionException.class,
        () -> IncidentService.createService(directClient).reportIncident(ALERT, constant(0), MS).join()).getCause());

    final var wrapped = rateLimited("61");
    final var wrappedClient = new StubClient(Integer.MAX_VALUE, true);
    wrappedClient.wrapFailures = true;
    wrappedClient.failureFactory = () -> wrapped;
    assertSame(wrapped, assertThrows(CompletionException.class,
        () -> IncidentService.createService(wrappedClient).reportIncident(ALERT, constant(0), MS).join()).getCause());

    final var runtimeCause = new IllegalStateException("cause-k");
    final var checkedClient = new StubClient(Integer.MAX_VALUE, true);
    checkedClient.failureFactory = () -> new WaitingCheckedClientException(runtimeCause, Duration.ofSeconds(61));
    assertSame(runtimeCause, assertThrows(CompletionException.class,
        () -> IncidentService.createService(checkedClient).reportIncident(ALERT, constant(0), MS).join()).getCause());
    assertEquals(1, checkedClient.calls.get());

    final var checkedCause = new Exception("cause-k2");
    final var bareCheckedClient = new StubClient(Integer.MAX_VALUE, true);
    bareCheckedClient.failureFactory = () -> new WaitingCheckedClientException(checkedCause, Duration.ofSeconds(61));
    final var bare = assertThrows(CompletionException.class,
        () -> IncidentService.createService(bareCheckedClient).reportIncident(ALERT, constant(0), MS).join());
    assertSame(checkedCause, assertInstanceOf(RuntimeException.class, bare.getCause()).getCause());
  }

  /// (k) A retryable checked exception's wait within the limit is served.
  @Test
  void aCheckedExceptionsWaitIsServed() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> new WaitingCheckedClientException(new IllegalStateException(), Duration.ofSeconds(2));
    final var delays = new RecordingDelays();
    new IncidentServiceVal(client, Duration.ofMinutes(1), delays).reportIncident(ALERT, constant(0), MS).join();
    assertEquals(ms(2_000), delays.requests);
  }

  /// (l) A `retryAfter()` that throws is no wait.
  @Test
  void aThrowingRetryAfterIsNoWait() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> new WaitingClientException(true, () -> {
      throw new IllegalStateException("unreadable");
    });
    final var delays = new RecordingDelays();
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      assertEquals("success", new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .reportIncident(ALERT, constant(0), MS).join().status());
      assertFalse(recorder.logged("Retry-After"));
    }
    assertEquals(List.of(), delays.requests);
    assertEquals(2, client.calls.get());
  }

  /// (l) B5: a non-retryable failure is not retried whatever its wait, and its log line gains
  /// the wait, rounded to the call's unit; a failure without one logs today's line.
  @Test
  void aWaitIsAppendedToTheFailureLogLine() {
    final var fatal = new TestClientException(false, new RetryAfterHttpResponse(429, "30"), 0);
    final var client = new StubClient(Integer.MAX_VALUE, false);
    client.failureFactory = () -> fatal;
    final var delays = new RecordingDelays();
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .resolveIncident("key-l", constant(0), MS);
      assertSame(fatal, assertThrows(CompletionException.class, future::join).getCause());
      assertEquals(List.of(
          "Failure Count: 1, Last Delay: 0 MILLISECONDS, to resolve incident with key 'key-l'., Retry-After: 30000 MILLISECONDS"
      ), recorder.messages());
    }
    assertEquals(List.of(), delays.requests);
    assertEquals(1, client.calls.get());

    final var wrapped = new StubClient(2, true);
    wrapped.wrapFailures = true;
    wrapped.failureFactory = inTurn(rateLimited("2"), new TestClientException(true, new TestHttpResponse(503), 42));
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      new IncidentServiceVal(wrapped, Duration.ofMinutes(1), new RecordingDelays())
          .resolveIncident("key-l", constant(0), TimeUnit.SECONDS).join();
      assertEquals(List.of(
          "Http Error Code: 429, Service Error Code: ?, Failure Count: 1, Last Delay: 0 SECONDS, Service Errors: [], to resolve incident with key 'key-l'., Retry-After: 2 SECONDS",
          "Http Error Code: 503, Service Error Code: 42, Failure Count: 2, Last Delay: 0 SECONDS, Service Errors: [], to resolve incident with key 'key-l'."
      ), recorder.messages());
    }
  }

  /// (m) The longest header there is saturates: refused under any finite limit, and under a
  /// limit that admits it, served as Long.MAX_VALUE of the call's unit without throwing.
  @Test
  void theLongestWaitSaturates() {
    final var refused = rateLimited("9223372036854775807");
    final var client = new StubClient(Integer.MAX_VALUE, true);
    client.failureFactory = () -> refused;
    assertSame(refused, assertThrows(CompletionException.class,
        () -> new IncidentServiceVal(client, Duration.ofDays(365_000), new RecordingDelays())
            .reportIncident(ALERT, constant(0), MS).join()).getCause());

    final var served = new StubClient(1, true);
    served.failureFactory = () -> rateLimited("9223372036854775807");
    final var delays = new RecordingDelays();
    assertEquals("success", new IncidentServiceVal(served, Duration.ofSeconds(Long.MAX_VALUE), delays)
        .reportIncident(ALERT, constant(0), MS).join().status());
    assertEquals(ms(Long.MAX_VALUE), delays.requests);
  }

  /// (n) B8: a client that throws on the attempt made after a wait fails the returned future.
  @Test
  void aThrowAfterAWaitFailsTheFuture() {
    final var thrown = new IllegalStateException("thrown on the delayed attempt");
    final var client = new StubClient(1, true);
    client.failureFactory = () -> rateLimited("30");
    client.thrownSynchronously = thrown;
    final var delays = new RecordingDelays();
    final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays).reportIncident(ALERT, constant(0), MS);
    assertTrue(future.isDone());
    assertSame(thrown, assertThrows(CompletionException.class, future::join).getCause());
    assertEquals(ms(30_000), delays.requests);
    assertEquals(2, client.calls.get());
  }

  /// A second delayed-executor seam: records each request when work is submitted to it and
  /// queues the work instead of running it, so a test can look at the returned future before
  /// the delayed attempt runs. A task that throws is dropped, as a pool thread would drop it,
  /// and kept for the test to see.
  private static final class QueuedDelays implements IncidentServiceVal.DelayedExecutorFactory {

    private final List<Delay> requests = new ArrayList<>();
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<RuntimeException> escaped = new ArrayList<>();

    @Override
    public Executor delayedExecutor(final long delay, final TimeUnit unit) {
      return task -> {
        requests.add(new Delay(delay, unit));
        queue.add(task);
      };
    }

    private void drain() {
      for (Runnable task; (task = queue.poll()) != null; ) {
        try {
          task.run();
        } catch (final RuntimeException dropped) {
          escaped.add(dropped);
        }
      }
    }
  }

  /// (n) B8 with the delayed attempt run only after `reportIncident` has returned: the future
  /// waits for it, then fails with what the client threw synchronously on it.
  @Test
  void aThrowOnAQueuedDelayedAttemptFailsTheFuture() {
    final var thrown = new IllegalStateException("thrown on the queued delayed attempt");
    final var client = new StubClient(1, true);
    client.failureFactory = () -> rateLimited("30");
    client.thrownSynchronously = thrown;
    final var delays = new QueuedDelays();
    final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays).reportIncident(ALERT, constant(0), MS);

    assertFalse(future.isDone());
    assertEquals(ms(30_000), delays.requests);
    assertEquals(1, delays.queue.size());
    assertEquals(1, client.calls.get());

    delays.drain();

    assertTrue(future.isDone());
    assertSame(thrown, assertThrows(CompletionException.class, future::join).getCause());
    assertEquals(List.of(), delays.escaped);
    assertEquals(2, client.calls.get());
    assertEquals(ms(30_000), delays.requests);
  }

  /// (n) The queued delayed attempt's success completes the future with its response.
  @Test
  void aQueuedDelayedAttemptsSuccessCompletesTheFuture() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> rateLimited("30");
    final var delays = new QueuedDelays();
    final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays).reportIncident(ALERT, constant(0), MS);

    assertFalse(future.isDone());
    assertEquals(ms(30_000), delays.requests);
    assertEquals(1, delays.queue.size());
    assertEquals(1, client.calls.get());

    delays.drain();

    assertTrue(future.isDone());
    final var response = future.join();
    assertEquals("alert-key", response.key());
    assertEquals("success", response.status());
    assertEquals(List.of(), delays.escaped);
    assertEquals(2, client.calls.get());
  }

  /// A retry-delay function negative for attempt 0 gives up before any attempt: every overload
  /// that reaches it returns null, the client is never called and no delay is requested.
  @Test
  void aNegativeFirstDelayMakesNoAttempt() {
    final List<java.util.function.Function<IncidentService, CompletableFuture<IncidentResponse>>> calls = List.of(
        service -> service.reportIncident(ALERT, _ -> -1, MS),
        service -> service.resolveIncident("key-c1", _ -> -1, MS),
        service -> service.reportIncident(ALERT, -1, 1, 1, MS),
        service -> service.resolveIncident("key-c1", -1, 1, 1, MS));
    for (int i = 0; i < calls.size(); ++i) {
      final var client = new StubClient(0, true);
      final var delays = new RecordingDelays();
      assertNull(calls.get(i).apply(new IncidentServiceVal(client, Duration.ofMinutes(1), delays)), "call " + i);
      assertEquals(0, client.calls.get(), "call " + i);
      assertEquals(List.of(), client.reports, "call " + i);
      assertEquals(List.of(), client.resolvedKeys, "call " + i);
      assertEquals(List.of(), delays.requests, "call " + i);
    }
  }

  /// B6 compares the exact wait with the limit, before rounding: 30.2 s is within a 30.5 s
  /// limit although it rounds up to 31 s, so it is served as 31 s and the next attempt made.
  @Test
  void theLimitComparesTheWaitBeforeRounding() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> new WaitingClientException(true, () -> Duration.ofMillis(30_200));
    final var delays = new RecordingDelays();
    final var response = new IncidentServiceVal(client, Duration.ofMillis(30_500), delays)
        .reportIncident(ALERT, constant(0), TimeUnit.SECONDS).join();
    assertEquals("success", response.status());
    assertEquals(List.of(new Delay(31, TimeUnit.SECONDS)), delays.requests);
    assertEquals(2, client.calls.get());
  }

  /// B6 holds regardless of `d`: a wait over the limit is refused even when the attempt's own
  /// delay already covers it.
  @Test
  void theLimitHoldsWhenTheStepDelayCoversTheWait() {
    final var failure = rateLimited("31");
    final var client = new StubClient(1, true);
    client.failureFactory = () -> failure;
    final var delays = new RecordingDelays();
    final var future = new IncidentServiceVal(client, Duration.ofSeconds(30), delays)
        .reportIncident(ALERT, n -> n == 0 ? 40_000 : 0, MS);
    assertSame(failure, assertThrows(CompletionException.class, future::join).getCause());
    assertEquals(ms(40_000), delays.requests, "only the failed attempt's own delay");
    assertEquals(1, client.calls.get());
  }

  /// B2: `canBeRetried` is decided before the retry-delay function is asked about the next
  /// attempt, so a non-retryable failure surfaces as itself, with or without a wait, and the
  /// function sees attempt 0 alone.
  @Test
  void aNonRetryableFailureNeverAsksForTheNextDelay() {
    for (final var fatal : List.of(
        new TestClientException(false, new RetryAfterHttpResponse(429, "30"), 0),
        new TestClientException(false))) {
      final var client = new StubClient(Integer.MAX_VALUE, false);
      client.failureFactory = () -> fatal;
      final var delays = new RecordingDelays();
      final var seen = new ArrayList<Long>();
      final var future = new IncidentServiceVal(client, Duration.ofMinutes(1), delays)
          .reportIncident(ALERT, n -> {
            seen.add(n);
            return n == 0 ? 0 : -1;
          }, MS);
      assertSame(fatal, assertThrows(CompletionException.class, future::join).getCause());
      assertEquals(List.of(0L), seen);
      assertEquals(List.of(), delays.requests);
      assertEquals(1, client.calls.get());
    }
  }

  /// (o) The limit is part of a service's identity; its text is today's.
  @Test
  void servicesForTheSameClientAndLimitAreEqual() {
    final var client = new StubClient(0, true);
    final var one = IncidentService.createService(client, Duration.ofSeconds(5));
    final var two = IncidentService.createService(client, Duration.ofSeconds(5));
    assertEquals(one, two);
    assertEquals(one.hashCode(), two.hashCode());
    assertNotEquals(one, IncidentService.createService(client, Duration.ofSeconds(6)));
    assertEquals(IncidentService.createService(client), IncidentService.createService(client));
    assertEquals("IncidentServiceVal[client=" + client + ']', one.toString());
    assertEquals("IncidentServiceVal[client=" + client + ']', IncidentService.createService(client).toString());
  }
}
