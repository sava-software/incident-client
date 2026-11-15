package software.sava.incident.webhook;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.hardening.support.JulRecorder;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;
import software.sava.incident.core.api.IncidentService;
import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.webhook.exceptions.WebhookRequestException;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/// The incident adapter following a refusal whose body names the destination's successor
/// ([WebhookFormat#movedTo(String)]): a Telegram basic group upgraded to a supergroup, and
/// everything that must not be followed.
final class WebhookMigrationTests {

  private static final String WEBHOOK_LOGGER = WebhookIncidentClient.class.getPackageName();
  private static final String SERVICE_LOGGER = IncidentService.class.getPackageName();

  private static final Logger FOLLOW_LOGGER = Logger.getLogger(WEBHOOK_LOGGER);
  private static final Logger RETRY_LOGGER = Logger.getLogger(SERVICE_LOGGER);
  private static Level previousFollowLevel;
  private static Level previousRetryLevel;

  @BeforeAll
  static void silenceLogging() {
    // every followed report logs a WARNING, and the service logs every failed attempt with its
    // stack trace; JulRecorder re-enables a logger for the tests that assert on its records
    previousFollowLevel = FOLLOW_LOGGER.getLevel();
    previousRetryLevel = RETRY_LOGGER.getLevel();
    FOLLOW_LOGGER.setLevel(Level.OFF);
    RETRY_LOGGER.setLevel(Level.OFF);
  }

  @AfterAll
  static void restoreLogging() {
    FOLLOW_LOGGER.setLevel(previousFollowLevel);
    RETRY_LOGGER.setLevel(previousRetryLevel);
  }

  /// The Bot API's 400 for a basic group that was upgraded to a supergroup.
  private static final String UPGRADED = """
      {"ok":false,"error_code":400,"description":"Bad Request: group chat was upgraded to a supergroup chat","parameters":{"migrate_to_chat_id":-1001234567890}}""";

  /// A Bot API 400 that names no successor.
  private static final String CHAT_NOT_FOUND = """
      {"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""";

  /// The Bot API's success document for a message the supergroup received.
  private static final String SENT = """
      {"ok":true,"result":{"message_id":7,"chat":{"id":-1001234567890}}}""";

  private static final IncidentAlert ALERT = IncidentAlert.build()
      .key("dedup-1")
      .summary("summary-1")
      .severity(IncidentSeverity.ERROR)
      .create();

  /// The configured basic group, in a topic, which a basic group never applied.
  private static final TelegramTextFormat BASIC = new TelegramTextFormat("-123456789", 42);

  private static final String BASIC_BODY = """
      {"chat_id":"-123456789","message_thread_id":42,"text":"[ERROR] summary-1\\nKey: dedup-1","link_preview_options":{"is_disabled":true}}""";

  private static final String SUPERGROUP_BODY = """
      {"chat_id":"-1001234567890","text":"[ERROR] summary-1\\nKey: dedup-1","link_preview_options":{"is_disabled":true}}""";

  private static final String WARNING_TEXT = "Webhook receiver refused TelegramTextFormat[chatId=-123456789, "
      + "messageThreadId=42, silentSeverities=[]] with status 400 and named "
      + "TelegramTextFormat[chatId=-1001234567890, messageThreadId=0, silentSeverities=[]] in its place; "
      + "sending the alert once more with it. Update the configuration to match.";

  /// A fixed-status response with no headers, so no `Retry-After` wait.
  private record StubResponse(int statusCode) implements HttpResponse<byte[]> {

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (_, _) -> true);
    }

    @Override
    public byte[] body() {
      return null;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return ENDPOINT;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final URI ENDPOINT = URI.create("https://api.telegram.org/bot123456:secret-token/sendMessage");

  private static WebhookRequestException refusal(final int statusCode, final String body) {
    return new WebhookRequestException(new StubResponse(statusCode), body, null);
  }

  private record Post(String body, WebhookFormat format) {
  }

  /// The queued answer of a client that returns null where a future is due.
  private static final Object NO_FUTURE = new Object();

  /// Answers each `post(body, format)` with the next queued answer, already complete: a future
  /// to return, a RuntimeException to throw, or [#NO_FUTURE] to return null. Records every
  /// post, the thrown ones too.
  private static final class StubClient implements WebhookClient {

    private final ConcurrentLinkedQueue<Object> answers = new ConcurrentLinkedQueue<>();
    private final List<Post> posts = new ArrayList<>();

    private StubClient answer(final Object... answers) {
      this.answers.addAll(List.of(answers));
      return this;
    }

    @Override
    public CompletableFuture<String> post(final String jsonBody) {
      throw new AssertionError("the adapter must post with its format");
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<String> post(final String jsonBody, final WebhookFormat format) {
      posts.add(new Post(jsonBody, format));
      final var answer = answers.poll();
      if (answer == NO_FUTURE) {
        return null;
      }
      return switch (answer) {
        case null -> throw new AssertionError("no answer queued for post " + posts.size());
        case final RuntimeException thrown -> throw thrown;
        case final CompletableFuture<?> future -> (CompletableFuture<String>) future;
        default -> throw new AssertionError("not an answer: " + answer);
      };
    }

    @Override
    public URI endpoint() {
      return ENDPOINT;
    }

    @Override
    public HttpClient httpClient() {
      return null;
    }
  }

  private static CompletableFuture<String> failed(final Throwable failure) {
    return CompletableFuture.failedFuture(failure);
  }

  private static CompletableFuture<String> succeeded(final String body) {
    return CompletableFuture.completedFuture(body);
  }

  /// What a dependent stage of `future` receives as its failure.
  private static Throwable dependentView(final CompletableFuture<?> future) {
    return future.handle((_, failure) -> failure).join();
  }

  /// A format that counts the times it is asked whether it moved, and never moves.
  private static final class CountingFormat implements WebhookFormat {

    private final AtomicInteger asked = new AtomicInteger();

    @Override
    public String render(final IncidentAlert alert) {
      return "{\"counted\":true}";
    }

    @Override
    public WebhookFormat movedTo(final String responseBody) {
      asked.incrementAndGet();
      return null;
    }
  }

  /// A format that always names its successor, `generation + 1`, counting the times it is asked.
  private record MovingFormat(int generation, AtomicInteger asked) implements WebhookFormat {

    @Override
    public String render(final IncidentAlert alert) {
      return "{\"generation\":" + generation + '}';
    }

    @Override
    public WebhookFormat movedTo(final String responseBody) {
      asked.incrementAndGet();
      return new MovingFormat(generation + 1, asked);
    }
  }

  @Test
  void followsAnUpgradedGroupOnce() {
    final var client = new StubClient().answer(failed(refusal(400, UPGRADED)), succeeded(" " + SENT + "\n"));
    final var response = client.incidentClient(BASIC).reportIncident(ALERT).join();

    assertEquals(2, client.posts.size());
    assertEquals(BASIC_BODY, client.posts.get(0).body());
    assertSame(BASIC, client.posts.get(0).format());
    assertEquals(SUPERGROUP_BODY, client.posts.get(1).body());
    assertEquals(new TelegramTextFormat("-1001234567890"), client.posts.get(1).format());
    assertEquals("dedup-1", response.key());
    assertEquals(SENT, response.status());
    assertNull(response.url());
  }

  /// An empty answer to the re-send is `delivered`, as for any report.
  @Test
  void anEmptyResendAnswerIsDelivered() {
    final var client = new StubClient().answer(failed(refusal(400, UPGRADED)), succeeded(""));
    assertEquals("delivered", client.incidentClient(BASIC).reportIncident(ALERT).join().status());
    assertEquals(2, client.posts.size());
  }

  /// Nothing is remembered: every report tries the configured chat first.
  @Test
  void everyReportFollowsAgain() {
    final var client = new StubClient().answer(
        failed(refusal(400, UPGRADED)), succeeded(SENT),
        failed(refusal(400, UPGRADED)), succeeded(SENT));
    final var incidentClient = (WebhookIncidentClient) client.incidentClient(BASIC);
    assertEquals(SENT, incidentClient.reportIncident(ALERT).join().status());
    assertEquals(SENT, incidentClient.reportIncident(ALERT).join().status());

    assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY, BASIC_BODY, SUPERGROUP_BODY),
        client.posts.stream().map(Post::body).toList());
    assertSame(BASIC, incidentClient.format());
    assertEquals("WebhookIncidentClient{format=TelegramTextFormat[chatId=-123456789, messageThreadId=42, "
        + "silentSeverities=[]], client=" + client + '}', incidentClient.toString());
  }

  /// The re-send's failure is the caller's, at the usual depth, with the first refusal in it.
  @Test
  void aFailedResendIsWhatTheCallerSees() {
    for (final boolean wrapped : new boolean[]{false, true}) {
      final var original = refusal(400, UPGRADED);
      final var forbidden = refusal(403, """
          {"ok":false,"error_code":403,"description":"Forbidden: bot is not a member of the supergroup chat"}""");
      final var client = new StubClient().answer(
          failed(original),
          failed(wrapped ? new CompletionException(forbidden) : forbidden));
      final var future = client.incidentClient(BASIC).reportIncident(ALERT);

      final var ex = assertThrows(CompletionException.class, future::join);
      assertSame(forbidden, ex.getCause());
      assertArrayEquals(new Throwable[]{original}, forbidden.getSuppressed());
      assertArrayEquals(new Throwable[0], ex.getSuppressed());
      assertArrayEquals(new Throwable[0], original.getSuppressed());
      final var view = assertInstanceOf(CompletionException.class, dependentView(future));
      assertSame(forbidden, view.getCause());
      assertEquals(2, client.posts.size());
    }
  }

  /// A re-send failing with the very refusal it followed adds nothing: a throwable cannot
  /// suppress itself.
  @Test
  void aResendFailingWithTheSameRefusalSuppressesNothing() {
    final var original = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original), failed(original));
    final var ex = assertThrows(CompletionException.class, () -> client.incidentClient(BASIC).reportIncident(ALERT).join());
    assertSame(original, ex.getCause());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
    assertArrayEquals(new Throwable[0], ex.getSuppressed());
    assertEquals(2, client.posts.size());
  }

  /// A re-send failing with a CompletionException that carries no cause takes the refusal
  /// itself.
  @Test
  void aCauselessResendFailureTakesTheRefusal() {
    final var original = refusal(400, UPGRADED);
    final var causeless = new CompletionException((Throwable) null);
    final var client = new StubClient().answer(failed(original), failed(causeless));
    final var ex = assertThrows(CompletionException.class, () -> client.incidentClient(BASIC).reportIncident(ALERT).join());
    assertSame(causeless, ex);
    assertArrayEquals(new Throwable[]{original}, causeless.getSuppressed());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
  }

  /// The re-send's answer is never asked about, so a report makes at most two requests.
  @Test
  void theResendsRefusalIsNeverFollowed() {
    final var original = refusal(400, UPGRADED);
    final var again = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original), failed(again));
    final var ex = assertThrows(CompletionException.class, () -> client.incidentClient(BASIC).reportIncident(ALERT).join());
    assertSame(again, ex.getCause());
    assertArrayEquals(new Throwable[]{original}, again.getSuppressed());
    assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), client.posts.stream().map(Post::body).toList());

    // a format that would move forever is asked once
    final var asked = new AtomicInteger();
    final var moving = new StubClient().answer(failed(refusal(410, "gone")), failed(refusal(410, "gone")));
    assertThrows(CompletionException.class,
        () -> moving.incidentClient(new MovingFormat(0, asked)).reportIncident(ALERT).join());
    assertEquals(1, asked.get());
    assertEquals(List.of("{\"generation\":0}", "{\"generation\":1}"), moving.posts.stream().map(Post::body).toList());
  }

  /// Unfollowed, a failure reaches the caller and a dependent stage exactly as an adapter that
  /// never follows delivers it: the same throwable, in one CompletionException.
  @Test
  void anUnfollowedFailureIsTheSameThrowableAtTheSameDepth() {
    for (final boolean wrapped : new boolean[]{false, true}) {
      final var original = refusal(400, CHAT_NOT_FOUND);
      final var client = new StubClient().answer(failed(wrapped ? new CompletionException(original) : original));
      final var future = client.incidentClient(BASIC).reportIncident(ALERT);

      final var ex = assertThrows(CompletionException.class, future::join);
      assertSame(original, ex.getCause());
      final var view = assertInstanceOf(CompletionException.class, dependentView(future));
      assertSame(original, view.getCause());
      assertArrayEquals(new Throwable[0], original.getSuppressed());
      assertArrayEquals(new Throwable[0], ex.getSuppressed());
      assertEquals(1, client.posts.size());

      // the shape the adapter had before it followed anything
      final var reference = (wrapped ? failed(new CompletionException(original)) : failed(original))
          .thenApply(String::strip);
      final var referenceView = assertInstanceOf(CompletionException.class, dependentView(reference));
      assertSame(original, referenceView.getCause());
    }
  }

  /// A failure that arrives after `reportIncident` returned is followed, or not, the same way.
  @Test
  void aLaterFailureIsJudgedTheSameWay() {
    final var pending = new CompletableFuture<String>();
    final var original = refusal(400, CHAT_NOT_FOUND);
    final var client = new StubClient().answer(pending);
    final var future = client.incidentClient(BASIC).reportIncident(ALERT);
    assertFalse(future.isDone());
    pending.completeExceptionally(new CompletionException(original));
    final var view = assertInstanceOf(CompletionException.class, dependentView(future));
    assertSame(original, view.getCause());
    assertEquals(1, client.posts.size());

    final var pendingUpgrade = new CompletableFuture<String>();
    final var followed = new StubClient().answer(pendingUpgrade, succeeded(SENT));
    final var followedFuture = followed.incidentClient(BASIC).reportIncident(ALERT);
    assertEquals(1, followed.posts.size());
    pendingUpgrade.completeExceptionally(new CompletionException(refusal(400, UPGRADED)));
    assertEquals(SENT, followedFuture.join().status());
    assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), followed.posts.stream().map(Post::body).toList());
  }

  /// A 429 or a 5xx is retried with its wait, never followed, whatever its body says.
  @Test
  void aRetryableRefusalIsNeverFollowed() {
    for (final int status : new int[]{429, 500, 503}) {
      final var original = refusal(status, UPGRADED);
      final var client = new StubClient().answer(failed(original));
      final var ex = assertThrows(CompletionException.class, () -> client.incidentClient(BASIC).reportIncident(ALERT).join());
      assertSame(original, ex.getCause());
      assertEquals(1, client.posts.size(), Integer.toString(status));

      final var counting = new CountingFormat();
      final var countingClient = new StubClient().answer(failed(refusal(status, UPGRADED)));
      assertThrows(CompletionException.class, () -> countingClient.incidentClient(counting).reportIncident(ALERT).join());
      assertEquals(0, counting.asked.get(), Integer.toString(status));
    }
    // the statuses either side of 429, and below 500, are refusals that are asked about
    for (final int status : new int[]{428, 430, 499}) {
      final var counting = new CountingFormat();
      final var client = new StubClient().answer(failed(refusal(status, UPGRADED)));
      assertThrows(CompletionException.class, () -> client.incidentClient(counting).reportIncident(ALERT).join());
      assertEquals(1, counting.asked.get(), Integer.toString(status));
    }
  }

  @Test
  void aTransportFailureIsNeverFollowed() {
    final var ioFailure = new UncheckedIOException(new IOException("connection reset"));
    final var checked = new IOException("connection refused");
    final var noResponse = new WebhookRequestException(null, UPGRADED, null);
    for (final Throwable failure : List.of(
        ioFailure, new CompletionException(ioFailure), checked, new CompletionException(checked),
        noResponse, new CompletionException(noResponse))) {
      final var counting = new CountingFormat();
      final var client = new StubClient().answer(failed(failure));
      final var future = client.incidentClient(counting).reportIncident(ALERT);
      final var view = assertInstanceOf(CompletionException.class, dependentView(future));
      assertSame(failure instanceof CompletionException ? failure.getCause() : failure, view.getCause());
      assertEquals(0, counting.asked.get(), failure.toString());
      assertEquals(1, client.posts.size());
    }
    // a Telegram format would read the move from that body: it is never asked
    final var client = new StubClient().answer(failed(noResponse));
    assertThrows(CompletionException.class, () -> client.incidentClient(BASIC).reportIncident(ALERT).join());
    assertEquals(1, client.posts.size());
  }

  @Test
  void aNonWebhookFailureIsNeverFollowed() {
    final var unsupported = new UnsupportedOperationException(UPGRADED);
    for (final Throwable failure : List.of(unsupported, new CompletionException(unsupported), new AssertionError(UPGRADED))) {
      final var counting = new CountingFormat();
      final var client = new StubClient().answer(failed(failure));
      final var view = assertInstanceOf(CompletionException.class, dependentView(client.incidentClient(counting).reportIncident(ALERT)));
      assertSame(failure instanceof CompletionException ? failure.getCause() : failure, view.getCause());
      assertEquals(0, counting.asked.get());
      assertEquals(1, client.posts.size());
    }
  }

  /// A client that throws, on the re-send, the very refusal it failed the first post with
  /// leaves that refusal the caller's with nothing suppressed: a throwable cannot suppress
  /// itself, and trying would replace a refusal that cannot be retried with a failure that
  /// can.
  @Test
  void aResendThrowingTheSameRefusalSuppressesNothing() {
    final var original = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original), original);
    final var future = client.incidentClient(BASIC).reportIncident(ALERT);
    final var ex = assertThrows(CompletionException.class, future::join);
    assertSame(original, ex.getCause());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
    assertSame(original, dependentView(future).getCause());
    assertEquals(2, client.posts.size());
  }

  /// A client that returns no future for the re-send leaves the first refusal the caller's,
  /// with a NullPointerException saying so suppressed in it.
  @Test
  void aResendWithoutAFutureSurfacesTheOriginal() {
    final var original = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original), NO_FUTURE);
    final var future = client.incidentClient(BASIC).reportIncident(ALERT);
    final var ex = assertThrows(CompletionException.class, future::join);
    assertSame(original, ex.getCause());
    assertEquals(1, original.getSuppressed().length);
    final var noFuture = assertInstanceOf(NullPointerException.class, original.getSuppressed()[0]);
    assertEquals("The client returned no future for the re-send.", noFuture.getMessage());
    assertSame(original, dependentView(future).getCause());
    assertEquals(2, client.posts.size());
  }

  /// A moved format whose `equals` throws is no move either: the refusal stays the caller's
  /// and nothing more is sent.
  @Test
  void aMovedFormatWhoseEqualsThrowsIsNoMove() {
    final var incomparable = new WebhookFormat() {
      @Override
      public String render(final IncidentAlert alert) {
        return "{\"incomparable\":true}";
      }

      @Override
      public boolean equals(final Object other) {
        throw new IllegalStateException("faulty equals");
      }

      @Override
      public int hashCode() {
        return 1;
      }
    };
    final WebhookFormat moving = new WebhookFormat() {
      @Override
      public String render(final IncidentAlert alert) {
        return "{\"moving\":true}";
      }

      @Override
      public WebhookFormat movedTo(final String responseBody) {
        return incomparable;
      }
    };
    final var original = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original));
    final var future = client.incidentClient(moving).reportIncident(ALERT);
    final var ex = assertThrows(CompletionException.class, future::join);
    assertSame(original, ex.getCause());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
    assertEquals(1, client.posts.size());
  }

  /// A format whose `movedTo` throws, or names a format equal to itself, is not followed.
  @Test
  void aThrowingMovedToIsNoMove() {
    final var original = refusal(400, UPGRADED);
    final var asked = new AtomicInteger();
    final var throwing = new WebhookFormat() {
      @Override
      public String render(final IncidentAlert alert) {
        return "{\"throwing\":true}";
      }

      @Override
      public WebhookFormat movedTo(final String responseBody) {
        asked.incrementAndGet();
        throw new IllegalStateException("faulty format");
      }
    };
    final var client = new StubClient().answer(failed(original));
    final var ex = assertThrows(CompletionException.class, () -> client.incidentClient(throwing).reportIncident(ALERT).join());
    assertSame(original, ex.getCause());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
    assertEquals(1, asked.get());
    assertEquals(1, client.posts.size());
  }

  private record SameFormat(String name, AtomicInteger asked) implements WebhookFormat {

    @Override
    public String render(final IncidentAlert alert) {
      return "{\"same\":\"" + name + "\"}";
    }

    @Override
    public WebhookFormat movedTo(final String responseBody) {
      asked.incrementAndGet();
      // equal, never the same instance
      return new SameFormat(name, asked);
    }
  }

  @Test
  void anEqualMovedToIsNoMove() {
    final var original = refusal(400, UPGRADED);
    final var asked = new AtomicInteger();
    final var client = new StubClient().answer(failed(original));
    final var ex = assertThrows(CompletionException.class,
        () -> client.incidentClient(new SameFormat("room-1", asked)).reportIncident(ALERT).join());
    assertSame(original, ex.getCause());
    assertArrayEquals(new Throwable[0], original.getSuppressed());
    assertEquals(1, asked.get());
    assertEquals(1, client.posts.size());
  }

  /// The moved format's render, or the client's post, throwing synchronously leaves the first
  /// refusal the caller's, with what was thrown suppressed in it.
  @Test
  void aThrowingResendRenderSurfacesTheOriginal() {
    final var renderFailure = new IllegalStateException("cannot render");
    final var unrenderable = new WebhookFormat() {
      @Override
      public String render(final IncidentAlert alert) {
        throw renderFailure;
      }
    };
    final var original = refusal(400, UPGRADED);
    final var client = new StubClient().answer(failed(original));
    final WebhookFormat moving = new WebhookFormat() {
      @Override
      public String render(final IncidentAlert alert) {
        return "{\"moving\":true}";
      }

      @Override
      public WebhookFormat movedTo(final String responseBody) {
        return unrenderable;
      }
    };
    try (final var recorder = JulRecorder.attach(WEBHOOK_LOGGER)) {
      final var future = client.incidentClient(moving).reportIncident(ALERT);
      final var ex = assertThrows(CompletionException.class, future::join);
      assertSame(original, ex.getCause());
      assertArrayEquals(new Throwable[]{renderFailure}, original.getSuppressed());
      assertSame(original, dependentView(future).getCause());
      assertEquals(1, client.posts.size());
      // the moved format never rendered, so there is no second request to announce
      assertEquals(List.of(), recorder.records());
    }

    final var postFailure = new IllegalStateException("cannot post");
    final var secondOriginal = refusal(400, UPGRADED);
    final var throwingClient = new StubClient().answer(failed(secondOriginal), postFailure);
    try (final var recorder = JulRecorder.attach(WEBHOOK_LOGGER)) {
      final var ex = assertThrows(CompletionException.class,
          () -> throwingClient.incidentClient(BASIC).reportIncident(ALERT).join());
      assertSame(secondOriginal, ex.getCause());
      assertArrayEquals(new Throwable[]{postFailure}, secondOriginal.getSuppressed());
      assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), throwingClient.posts.stream().map(Post::body).toList());
      // the stale configuration is reported even though the re-send failed
      assertEquals(List.of(WARNING_TEXT), recorder.records().stream().map(r -> r.getMessage()).toList());
    }
  }

  /// A first render that throws still throws from `reportIncident` itself, before any post.
  @Test
  void aThrowingFirstRenderStillThrows() {
    final var renderFailure = new IllegalStateException("cannot render");
    final var client = new StubClient();
    final var incidentClient = client.incidentClient(alert -> {
      throw renderFailure;
    });
    assertSame(renderFailure, assertThrows(IllegalStateException.class, () -> incidentClient.reportIncident(ALERT)));
    assertEquals(0, client.posts.size());
  }

  @Test
  void theFollowIsLoggedAsAWarning() {
    final var client = new StubClient().answer(
        failed(refusal(400, UPGRADED)), succeeded(SENT),
        failed(refusal(400, UPGRADED)), failed(refusal(403, "forbidden")),
        failed(refusal(400, CHAT_NOT_FOUND)));
    final var incidentClient = client.incidentClient(BASIC);
    try (final var recorder = JulRecorder.attach(WEBHOOK_LOGGER)) {
      incidentClient.reportIncident(ALERT).join();
      final var records = recorder.records();
      assertEquals(1, records.size());
      final var record = records.getFirst();
      assertEquals(Level.WARNING, record.getLevel());
      assertEquals(WARNING_TEXT, record.getMessage());
      assertNull(record.getThrown());
      // never the endpoint, its credential, or its path
      for (final var secret : List.of(ENDPOINT.toString(), "secret-token", "bot123456", "sendMessage", "api.telegram.org")) {
        assertFalse(record.getMessage().contains(secret), secret);
      }

      // the next followed report logs again, whatever the re-send's outcome
      assertThrows(CompletionException.class, () -> incidentClient.reportIncident(ALERT).join());
      assertEquals(List.of(WARNING_TEXT, WARNING_TEXT), recorder.records().stream().map(r -> r.getMessage()).toList());

      // an unfollowed failure logs nothing
      assertThrows(CompletionException.class, () -> incidentClient.reportIncident(ALERT).join());
      assertEquals(2, recorder.records().size());
    }
  }

  /// The upgraded group's server: a 400 naming the supergroup for the basic group's chat id,
  /// the success document otherwise; records every request body.
  private static HttpServer telegramServer(final ConcurrentLinkedQueue<String> requests) throws IOException {
    final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      try (exchange) {
        final var body = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
        requests.add(body);
        final boolean basicGroup = body.contains("\"chat_id\":\"-123456789\"");
        final var response = (basicGroup ? UPGRADED : SENT + '\n').getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(basicGroup ? 400 : 200, response.length);
        exchange.getResponseBody().write(response);
      }
    });
    server.setExecutor(ForkJoinPool.commonPool());
    server.start();
    return server;
  }

  private static final String BOT_PATH = "/bot123456:secret-token/sendMessage";

  @Test
  void theWireFollowsTheUpgrade() throws IOException {
    final var requests = new ConcurrentLinkedQueue<String>();
    final var server = telegramServer(requests);
    try (final var recorder = JulRecorder.attach(WEBHOOK_LOGGER)) {
      final var endpoint = TestEndpoint.http(server.getAddress(), BOT_PATH);
      final var incidentClient = WebhookClient.clientBuilder()
          .endpoint(endpoint)
          .createClient()
          .incidentClient(BASIC);
      final var response = incidentClient.reportIncident(ALERT).join();

      assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), List.copyOf(requests));
      assertEquals(SENT, response.status());
      assertEquals("dedup-1", response.key());
      assertEquals(List.of(WARNING_TEXT), recorder.records().stream().map(r -> r.getMessage()).toList());
      assertFalse(recorder.records().getFirst().getMessage().contains(endpoint.toString()));
      assertFalse(recorder.records().getFirst().getMessage().contains(BOT_PATH));
    } finally {
      server.stop(0);
    }
  }

  /// A direct post is never followed: the caller gets the refusal and reads the move itself.
  @Test
  void theDirectPostNeverFollows() throws IOException {
    final var requests = new ConcurrentLinkedQueue<String>();
    final var server = telegramServer(requests);
    try {
      final var client = WebhookClient.clientBuilder()
          .endpoint(TestEndpoint.http(server.getAddress(), BOT_PATH))
          .createClient();
      final var ex = assertThrows(CompletionException.class, () -> client.post(BASIC_BODY, BASIC).join());
      final var refused = assertInstanceOf(WebhookRequestException.class, ex.getCause());
      assertEquals(400L, refused.errorCode());
      assertEquals(UPGRADED, refused.body());
      assertEquals(List.of(BASIC_BODY), List.copyOf(requests));

      // the recipe the README gives a direct caller
      assertEquals(-1001234567890L, TelegramTextFormat.botApiMigrateToChatId(refused.body()));
      final var moved = BASIC.movedTo(refused.body());
      assertEquals(SENT + '\n', client.post(moved.render(ALERT), moved).join());
      assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), List.copyOf(requests));
    } finally {
      server.stop(0);
    }
  }

  /// Two attempts at most, the second at once: the delays 0 and 0, then giving up.
  private static final LongUnaryOperator TWO_ATTEMPTS = numFailures -> numFailures < 2 ? 0 : -1;

  private static IncidentResponse report(final IncidentClient client, final LongUnaryOperator retryDelayFn) {
    return IncidentService.createService(client).reportIncident(ALERT, retryDelayFn, TimeUnit.MILLISECONDS).join();
  }

  /// The follow happens inside one attempt, so the service sees one success or one failure.
  @Test
  void theServiceSeesOneAttempt() {
    final var followed = new StubClient().answer(failed(refusal(400, UPGRADED)), succeeded(SENT));
    assertEquals(SENT, report(followed.incidentClient(BASIC), TWO_ATTEMPTS).status());
    assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY), followed.posts.stream().map(Post::body).toList());

    // a re-send refused for good ends the call after one attempt
    final var original = refusal(400, UPGRADED);
    final var forbidden = refusal(403, "forbidden");
    final var terminal = new StubClient().answer(failed(original), failed(forbidden));
    final var terminalEx = assertThrows(CompletionException.class, () -> report(terminal.incidentClient(BASIC), TWO_ATTEMPTS));
    assertSame(forbidden, terminalEx.getCause());
    assertArrayEquals(new Throwable[]{original}, forbidden.getSuppressed());
    assertEquals(2, terminal.posts.size());

    // a re-send the service may retry is retried, and each attempt follows again
    final var firstTooMany = refusal(429, "{\"ok\":false,\"error_code\":429}");
    final var lastTooMany = refusal(429, "{\"ok\":false,\"error_code\":429}");
    final var retried = new StubClient().answer(
        failed(refusal(400, UPGRADED)), failed(firstTooMany),
        failed(refusal(400, UPGRADED)), failed(lastTooMany));
    final var retriedEx = assertThrows(CompletionException.class, () -> report(retried.incidentClient(BASIC), TWO_ATTEMPTS));
    assertSame(lastTooMany, retriedEx.getCause());
    assertEquals(List.of(BASIC_BODY, SUPERGROUP_BODY, BASIC_BODY, SUPERGROUP_BODY),
        retried.posts.stream().map(Post::body).toList());

    // an unfollowed refusal is still terminal: one attempt, one request
    final var notFound = refusal(400, CHAT_NOT_FOUND);
    final var unfollowed = new StubClient().answer(failed(notFound));
    final var unfollowedEx = assertThrows(CompletionException.class, () -> report(unfollowed.incidentClient(BASIC), TWO_ATTEMPTS));
    assertSame(notFound, unfollowedEx.getCause());
    assertEquals(1, unfollowed.posts.size());
  }
}
