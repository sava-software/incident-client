package software.sava.incident.pagerduty.event.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.hardening.support.JulRecorder;
import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;
import software.sava.incident.pagerduty.exceptions.PagerDutyClientException;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/// Pins the retrying service wrapper. This mirrors `incident-core`'s `IncidentServiceTests`
/// against the PagerDuty-specific twin, which had no test of its own.
final class PagerDutyServiceTests {

  private static final String LOGGER_NAME = PagerDutyService.class.getPackageName();

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

  private static final PagerDutyEventPayload EVENT = PagerDutyEventPayload.build()
      .summary("summary")
      .source("source")
      .severity(PagerDutySeverity.error)
      .create();

  private static final PagerDutyChangeEventPayload CHANGE = PagerDutyChangeEventPayload.build()
      .summary("change-summary")
      .source("source")
      .create();

  private static PagerDutyEventResponse response(final String dedupKey) {
    return PagerDutyEventResponse.parser()
        .status("success")
        .message("Event processed")
        .dedupKey(dedupKey)
        .create();
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

  private static final class TestClientException extends RuntimeException implements PagerDutyClientException {

    private final boolean canBeRetried;
    private final HttpResponse<?> httpResponse;
    private final long errorCode;
    private final List<String> errors;

    private TestClientException(final boolean canBeRetried) {
      this(canBeRetried, null, 0, List.of());
    }

    private TestClientException(final boolean canBeRetried,
                                final HttpResponse<?> httpResponse,
                                final long errorCode,
                                final List<String> errors) {
      super(canBeRetried ? "retriable" : "fatal");
      this.canBeRetried = canBeRetried;
      this.httpResponse = httpResponse;
      this.errorCode = errorCode;
      this.errors = errors;
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
      return errors;
    }
  }

  private static final class TestCheckedClientException extends Exception implements PagerDutyClientException {

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

  private static final class StubClient implements PagerDutyEventClient {

    private final AtomicInteger calls = new AtomicInteger();
    private final int failures;
    private final boolean canBeRetried;
    private boolean wrapFailures;
    private Supplier<Throwable> failureFactory;
    private String lastRoutingKey;
    private String lastClientName;
    private String lastClientUrl;
    private String lastDedupKey;

    private StubClient(final int failures, final boolean canBeRetried) {
      this.failures = failures;
      this.canBeRetried = canBeRetried;
    }

    private CompletableFuture<PagerDutyEventResponse> respond(final String dedupKey) {
      if (calls.incrementAndGet() <= failures) {
        final var exception = failureFactory == null ? new TestClientException(canBeRetried) : failureFactory.get();
        return CompletableFuture.failedFuture(wrapFailures ? new CompletionException(exception) : exception);
      }
      return CompletableFuture.completedFuture(response(dedupKey));
    }

    @Override
    public String defaultClientName() {
      return "client-name";
    }

    @Override
    public String defaultClientUrl() {
      return "https://client.example.com";
    }

    @Override
    public String defaultRoutingKey() {
      return "routing-key";
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> acknowledgeEvent(final String routingKey, final String dedupKey) {
      this.lastRoutingKey = routingKey;
      return respond(dedupKey);
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> resolveEvent(final String routingKey, final String dedupKey) {
      this.lastRoutingKey = routingKey;
      this.lastDedupKey = dedupKey;
      return respond(dedupKey);
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                                  final String clientUrl,
                                                                  final String routingKey,
                                                                  final PagerDutyEventPayload payload) {
      this.lastClientName = clientName;
      this.lastClientUrl = clientUrl;
      this.lastRoutingKey = routingKey;
      return respond("trigger-" + payload.summary());
    }

    @Override
    public CompletableFuture<PagerDutyEventResponse> changeEvent(final String routingKey,
                                                                 final PagerDutyChangeEventPayload payload) {
      this.lastRoutingKey = routingKey;
      return respond("change-" + payload.summary());
    }

    @Override
    public URI endpoint() {
      return URI.create("https://events.pagerduty.com");
    }

    @Override
    public HttpClient httpClient() {
      return null;
    }
  }

  private static PagerDutyService service(final StubClient client) {
    return PagerDutyService.build().client(client).eventPrototype(EVENT).create();
  }

  @Test
  void builderRoundTripsAndChains() {
    final var client = new StubClient(0, true);
    final var builder = PagerDutyService.build();
    assertNotNull(builder);
    assertSame(builder, builder.client(client));
    assertSame(builder, builder.eventPrototype(EVENT));

    final var service = builder.create();
    assertNotNull(service);
    assertSame(client, service.client());
    assertSame(EVENT, service.eventPrototype());
  }

  @Test
  void eventFromPrototypeCarriesThePrototypeForward() {
    final var built = service(new StubClient(0, true)).eventFromPrototype().create();
    assertEquals(EVENT.summary(), built.summary());
    assertEquals(EVENT.source(), built.source());
    assertEquals(EVENT.severity(), built.severity());
  }

  @Test
  void retryDelayFnScalesByStepDelayAndCaps() {
    // below the cap the delay is numFailures * stepDelay -- not a division, offset, or constant
    assertEquals(15, PagerDutyService.createRetryDelayFn(5, 1_000).applyAsLong(3));
    assertEquals(1_000, PagerDutyService.createRetryDelayFn(5, 1_000).applyAsLong(1_000));
    assertEquals(15, PagerDutyService.createRetryDelayFn(10, 5, 1_000).applyAsLong(3));
  }

  @Test
  void retryDelayFnGivesUpOnlyPastMaxRetries() {
    final var fn = PagerDutyService.createRetryDelayFn(2, 1, 10);
    assertEquals(2, fn.applyAsLong(2), "at maxRetries the delay is still positive");
    assertTrue(fn.applyAsLong(3) < 0, "past maxRetries the caller must see a negative give-up signal");
  }

  @Test
  void triggerSucceedsAfterRetriableFailures() {
    final var client = new StubClient(2, true);
    final var response = service(client).triggerEvent(EVENT, 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("success", response.status());
    assertEquals(3, client.calls.get());
    // the default-route path must carry the client's own defaults through
    assertEquals("routing-key", client.lastRoutingKey);
    assertEquals("client-name", client.lastClientName);
    assertEquals("https://client.example.com", client.lastClientUrl);
  }

  @Test
  void triggerDoesNotRetryFatalFailures() {
    final var client = new StubClient(Integer.MAX_VALUE, false);
    final var future = service(client).triggerEvent(EVENT, 5, 1, 1, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertInstanceOf(TestClientException.class, thrown.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void triggerGivesUpAfterMaxRetries() {
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var future = service(client).triggerEvent(EVENT, 2, 1, 1, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
  }

  @Test
  void triggerGiveUpAfterDerivesMaxRetriesFromStepDelay() {
    // 10ms give-up / 5ms step -> 2 retries; maxDelay 0 keeps every retry immediate
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var future = service(client)
        .triggerEvent(EVENT, Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
  }

  @Test
  void triggerWithAnExplicitDelayFnRetriesOnThatSchedule() {
    final var client = new StubClient(1, true);
    final var response = service(client)
        .triggerEvent(EVENT, PagerDutyService.createRetryDelayFn(1, 1), TimeUnit.MILLISECONDS)
        .join();
    assertEquals("success", response.status());
    assertEquals(2, client.calls.get());
  }

  @Test
  void triggerReturnsNullWhenTheDelayFnGivesUpImmediately() {
    final var client = new StubClient(0, true);
    assertNull(service(client).triggerEvent(EVENT, numFailures -> -1, TimeUnit.MILLISECONDS));
    assertEquals(0, client.calls.get(), "a give-up signal must not reach the transport");
  }

  @Test
  void resolveRetriesUntilSuccessAndPassesTheDedupKey() {
    final var client = new StubClient(1, true);
    final var response = service(client).resolveEvent("key-1", 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("key-1", response.dedupKey());
    assertEquals("key-1", client.lastDedupKey);
    assertEquals("routing-key", client.lastRoutingKey);
    assertEquals(2, client.calls.get());
  }

  @Test
  void resolveOverloadsRejectANullDedupKey() {
    final var svc = service(new StubClient(0, true));
    assertNull(svc.resolveEvent((String) null, 5, 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((String) null, Duration.ofMillis(10), 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((String) null, 2, 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((String) null, numFailures -> 0, TimeUnit.MILLISECONDS));
  }

  @Test
  void resolveFromTriggerResponseUsesItsDedupKeyOnEveryOverload() {
    final var client = new StubClient(0, true);
    final var svc = service(client);
    final var trigger = response("from-trigger");

    assertEquals("from-trigger", svc.resolveEvent(trigger, 1, 1, TimeUnit.MILLISECONDS).join().dedupKey());
    assertEquals("from-trigger", client.lastDedupKey);
    assertEquals("from-trigger",
        svc.resolveEvent(trigger, Duration.ofMillis(10), 1, 1, TimeUnit.MILLISECONDS).join().dedupKey());
    assertEquals("from-trigger", svc.resolveEvent(trigger, 2, 1, 1, TimeUnit.MILLISECONDS).join().dedupKey());
    assertEquals("from-trigger",
        svc.resolveEvent(trigger, numFailures -> 0, TimeUnit.MILLISECONDS).join().dedupKey());
    assertEquals(4, client.calls.get());
  }

  @Test
  void resolveFromANullTriggerResponseIsNullOnEveryOverload() {
    final var client = new StubClient(0, true);
    final var svc = service(client);

    assertNull(svc.resolveEvent((PagerDutyEventResponse) null, 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((PagerDutyEventResponse) null, Duration.ofMillis(10), 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((PagerDutyEventResponse) null, 2, 1, 1, TimeUnit.MILLISECONDS));
    assertNull(svc.resolveEvent((PagerDutyEventResponse) null, numFailures -> 0, TimeUnit.MILLISECONDS));
    assertEquals(0, client.calls.get(), "a null trigger response must not reach the transport");
  }

  @Test
  void changeEventRetriesAndCarriesTheDefaultRoute() {
    final var client = new StubClient(2, true);
    final var response = service(client).changeEvent(CHANGE, 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("success", response.status());
    assertEquals("routing-key", client.lastRoutingKey);
    assertEquals(3, client.calls.get());
  }

  @Test
  void changeEventOverloadsAllReachTheTransport() {
    final var client = new StubClient(0, true);
    final var svc = service(client);

    assertNotNull(svc.changeEvent(CHANGE, 1, 1, TimeUnit.MILLISECONDS).join());
    assertNotNull(svc.changeEvent(CHANGE, Duration.ofMillis(10), 1, 1, TimeUnit.MILLISECONDS).join());
    assertNotNull(svc.changeEvent(CHANGE, 2, 1, 1, TimeUnit.MILLISECONDS).join());
    assertNotNull(svc.changeEvent(CHANGE, numFailures -> 0, TimeUnit.MILLISECONDS).join());
    assertEquals(4, client.calls.get());
  }

  @Test
  void changeEventGiveUpAfterDerivesMaxRetriesFromStepDelay() {
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var future = service(client)
        .changeEvent(CHANGE, Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
  }

  @Test
  void changeEventReturnsNullWhenTheDelayFnGivesUpImmediately() {
    final var client = new StubClient(0, true);
    assertNull(service(client).changeEvent(CHANGE, 0, numFailures -> -1, TimeUnit.MILLISECONDS));
    assertEquals(0, client.calls.get());
  }

  @Test
  void changeEventDoesNotRetryFatalFailures() {
    final var client = new StubClient(Integer.MAX_VALUE, false);
    final var future = service(client).changeEvent(CHANGE, 5, 1, 1, TimeUnit.MILLISECONDS);
    final var thrown = assertThrows(CompletionException.class, future::join);
    assertInstanceOf(TestClientException.class, thrown.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void wrappedClientExceptionsAreUnwrappedForTheRetryDecision() {
    // upstream stages deliver failures wrapped in a CompletionException
    final var retriable = new StubClient(1, true);
    retriable.wrapFailures = true;
    assertEquals("success",
        service(retriable).triggerEvent(EVENT, 5, 1, 1, TimeUnit.MILLISECONDS).join().status());
    assertEquals(2, retriable.calls.get());

    final var fatal = new StubClient(Integer.MAX_VALUE, false);
    fatal.wrapFailures = true;
    assertThrows(CompletionException.class,
        service(fatal).triggerEvent(EVENT, 5, 1, 1, TimeUnit.MILLISECONDS)::join);
    assertEquals(1, fatal.calls.get());
  }

  @Test
  void transportFailuresWithoutAClientCauseAreRetried() {
    final var client = new StubClient(1, true);
    client.failureFactory = () -> new RuntimeException("transport");
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      assertEquals("success",
          service(client).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS).join().status());
      assertTrue(recorder.logged("Failure Count: 1"));
      assertTrue(recorder.logged("to trigger event"));
      assertFalse(recorder.logged("Http Error Code"));
    }
    assertEquals(2, client.calls.get());
  }

  @Test
  void clientFailureLogsHttpAndServiceErrorCodes() {
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    client.failureFactory = () -> new TestClientException(true, new TestHttpResponse(503), 42, List.of("boom"));
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      assertEquals("success",
          service(client).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS).join().status());
      assertTrue(recorder.logged("Http Error Code: 503"));
      assertTrue(recorder.logged("Service Error Code: 42"));
      assertTrue(recorder.logged("boom"));
    }
    assertEquals(2, client.calls.get());
  }

  @Test
  void unknownClientCodesLogPlaceholders() {
    final var client = new StubClient(1, true);
    client.wrapFailures = true;
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      service(client).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertTrue(recorder.logged("Http Error Code: ?"));
      assertTrue(recorder.logged("Service Error Code: ?"));
    }
  }

  @Test
  void resolveFailureLogsTheDedupKeyAndChangeLogsThePayload() {
    final var resolveClient = new StubClient(1, true);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      assertEquals("key-log",
          service(resolveClient).resolveEvent("key-log", 5, 0, 0, TimeUnit.MILLISECONDS).join().dedupKey());
      assertTrue(recorder.logged("to resolve event with dedupe key 'key-log'"));
    }

    final var changeClient = new StubClient(1, true);
    try (final var recorder = JulRecorder.attach(LOGGER_NAME)) {
      service(changeClient).changeEvent(CHANGE, 5, 0, 0, TimeUnit.MILLISECONDS).join();
      assertTrue(recorder.logged("to send change event"));
    }
  }

  @Test
  void fatalCheckedFailurePropagatesItsRuntimeCause() {
    final var cause = new IllegalStateException("cause-a");
    final var client = new StubClient(Integer.MAX_VALUE, false);
    client.failureFactory = () -> new TestCheckedClientException(cause);
    final var thrown = assertThrows(CompletionException.class,
        service(client).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS)::join);
    assertSame(cause, thrown.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void fatalCheckedFailureWithoutARuntimeCauseIsWrapped() {
    final var cause = new Exception("cause-b");
    final var client = new StubClient(Integer.MAX_VALUE, false);
    client.failureFactory = () -> new TestCheckedClientException(cause);
    final var thrown = assertThrows(CompletionException.class,
        service(client).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS)::join);
    final var wrapped = assertInstanceOf(RuntimeException.class, thrown.getCause());
    assertSame(cause, wrapped.getCause());
    assertEquals(1, client.calls.get());
  }

  @Test
  void fatalCheckedFailurePropagatesThroughResolveAndChangeToo() {
    final var runtimeCause = new IllegalStateException("cause-c");

    final var resolveClient = new StubClient(Integer.MAX_VALUE, false);
    resolveClient.failureFactory = () -> new TestCheckedClientException(runtimeCause);
    assertSame(runtimeCause, assertThrows(CompletionException.class,
        service(resolveClient).resolveEvent("k", 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());

    final var changeClient = new StubClient(Integer.MAX_VALUE, false);
    changeClient.failureFactory = () -> new TestCheckedClientException(runtimeCause);
    assertSame(runtimeCause, assertThrows(CompletionException.class,
        service(changeClient).changeEvent(CHANGE, 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());

    final var checkedCause = new Exception("cause-d");
    final var changeWrapClient = new StubClient(Integer.MAX_VALUE, false);
    changeWrapClient.failureFactory = () -> new TestCheckedClientException(checkedCause);
    final var wrapped = assertInstanceOf(RuntimeException.class, assertThrows(CompletionException.class,
        service(changeWrapClient).changeEvent(CHANGE, 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());
    assertSame(checkedCause, wrapped.getCause());
  }

  @Test
  void aDirectClientExceptionIsClassifiedWithoutUnwrapping() {
    // the switch's PagerDutyClientException arm: the failure IS the client exception,
    // not a wrapper carrying one as its cause
    final var retriable = new StubClient(1, true);
    retriable.failureFactory = () -> new TestClientException(true);
    assertEquals("success",
        service(retriable).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS).join().status());
    assertEquals(2, retriable.calls.get());

    final var fatal = new StubClient(Integer.MAX_VALUE, false);
    fatal.failureFactory = () -> new TestClientException(false);
    assertThrows(CompletionException.class,
        service(fatal).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS)::join);
    assertEquals(1, fatal.calls.get());
  }

  @Test
  void aPositiveRetryDelayStillCompletesThroughTheDelayedExecutor() {
    // exercises the exceptionallyComposeAsync(delayedExecutor(...)) arm rather than the
    // synchronous one; a positive delay is otherwise only observable by wall clock
    final var client = new StubClient(1, true);
    final var response = service(client)
        .triggerEvent(EVENT, 5, 1, 1, TimeUnit.MILLISECONDS).join();
    assertEquals("success", response.status());
    assertEquals(2, client.calls.get());

    final var resolveClient = new StubClient(1, true);
    assertEquals("k", service(resolveClient)
        .resolveEvent("k", 5, 1, 1, TimeUnit.MILLISECONDS).join().dedupKey());

    final var changeClient = new StubClient(1, true);
    assertNotNull(service(changeClient).changeEvent(CHANGE, 5, 1, 1, TimeUnit.MILLISECONDS).join());
  }

  @Test
  void theStepDelayOverloadsAreDistinctFromTheMaxRetriesOnes() {
    // (payload, long stepDelay, long maxDelay, TimeUnit) and
    // (dedupeKey, long stepDelay, long maxDelay, TimeUnit) are separate methods from the
    // (…, int maxRetries, long, long, TimeUnit) ones and retry without any give-up bound
    final var triggerClient = new StubClient(2, true);
    assertEquals("success",
        service(triggerClient).triggerEvent(EVENT, 1L, 1L, TimeUnit.MILLISECONDS).join().status());
    assertEquals(3, triggerClient.calls.get());

    final var resolveClient = new StubClient(2, true);
    assertEquals("k4",
        service(resolveClient).resolveEvent("k4", 1L, 1L, TimeUnit.MILLISECONDS).join().dedupKey());
    assertEquals(3, resolveClient.calls.get());

    final var changeClient = new StubClient(2, true);
    assertNotNull(service(changeClient).changeEvent(CHANGE, 1L, 1L, TimeUnit.MILLISECONDS).join());
    assertEquals(3, changeClient.calls.get());

    // and they reject a null dedupe key on their own
    assertNull(service(new StubClient(0, true)).resolveEvent((String) null, 1L, 1L, TimeUnit.MILLISECONDS));
  }

  @Test
  void resolveGiveUpAfterDerivesMaxRetriesFromStepDelay() {
    // 10ms give-up / 5ms step -> 2 retries: the bound is a division of the two, so a
    // multiplication would let it retry far longer
    final var client = new StubClient(Integer.MAX_VALUE, true);
    final var future = service(client)
        .resolveEvent("key-giveup", Duration.ofMillis(10), 5, 0, TimeUnit.MILLISECONDS);
    assertThrows(CompletionException.class, future::join);
    assertEquals(3, client.calls.get());
  }

  @Test
  void resolveReturnsNullWhenTheDelayFnGivesUpImmediately() {
    final var client = new StubClient(0, true);
    assertNull(service(client).resolveEvent("k", numFailures -> -1, TimeUnit.MILLISECONDS));
    assertEquals(0, client.calls.get(), "a give-up signal must not reach the transport");
  }

  @Test
  void resolveFatalFailuresPropagateEachThrowableShape() {
    // the three arms of resolve's failure handler: a direct RuntimeException, a checked
    // failure carrying a RuntimeException cause, and one carrying neither
    final var direct = new RuntimeException("direct-runtime");
    final var directClient = new StubClient(Integer.MAX_VALUE, false);
    directClient.failureFactory = () -> new TestClientException(false);
    assertInstanceOf(TestClientException.class, assertThrows(CompletionException.class,
        service(directClient).resolveEvent("k", 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());

    final var runtimeCause = new IllegalStateException("resolve-cause");
    final var causeClient = new StubClient(Integer.MAX_VALUE, false);
    causeClient.failureFactory = () -> new TestCheckedClientException(runtimeCause);
    assertSame(runtimeCause, assertThrows(CompletionException.class,
        service(causeClient).resolveEvent("k", 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());

    final var checkedCause = new Exception("resolve-checked");
    final var wrapClient = new StubClient(Integer.MAX_VALUE, false);
    wrapClient.failureFactory = () -> new TestCheckedClientException(checkedCause);
    final var wrapped = assertInstanceOf(RuntimeException.class, assertThrows(CompletionException.class,
        service(wrapClient).resolveEvent("k", 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());
    assertSame(checkedCause, wrapped.getCause());
    assertNotSame(direct, wrapped);
  }

  @Test
  void aFatalFailureWithNoCauseIsWrappedAroundItself() {
    // the last arm of each failure handler: with a null cause the throwable itself is
    // wrapped, so the original stays reachable rather than being replaced by null.
    // resolveEvent and triggerEvent inline this; changeEvent routes through
    // throwRuntimeException -- all three need the same guarantee.
    final var changeClient = new StubClient(Integer.MAX_VALUE, false);
    changeClient.failureFactory = () -> new TestCheckedClientException(null);
    final var changeWrapped = assertInstanceOf(RuntimeException.class, assertThrows(CompletionException.class,
        service(changeClient).changeEvent(CHANGE, 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());
    assertInstanceOf(TestCheckedClientException.class, changeWrapped.getCause());

    final var resolveClient = new StubClient(Integer.MAX_VALUE, false);
    resolveClient.failureFactory = () -> new TestCheckedClientException(null);
    final var resolveWrapped = assertInstanceOf(RuntimeException.class, assertThrows(CompletionException.class,
        service(resolveClient).resolveEvent("k", 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());
    assertInstanceOf(TestCheckedClientException.class, resolveWrapped.getCause(),
        "a null cause must not become the wrapper's cause");

    final var triggerClient = new StubClient(Integer.MAX_VALUE, false);
    triggerClient.failureFactory = () -> new TestCheckedClientException(null);
    final var triggerWrapped = assertInstanceOf(RuntimeException.class, assertThrows(CompletionException.class,
        service(triggerClient).triggerEvent(EVENT, 5, 0, 0, TimeUnit.MILLISECONDS)::join).getCause());
    assertInstanceOf(TestCheckedClientException.class, triggerWrapped.getCause());
  }

  @Test
  void toStringNamesTheClientAndPrototype() {
    final var text = service(new StubClient(0, true)).toString();
    assertTrue(text.startsWith("PagerdutyServiceVal{client="));
    assertTrue(text.contains("eventPrototype="));
  }
}
