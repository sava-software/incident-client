package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.api.IncidentResponse;
import software.sava.incident.webhook.exceptions.WebhookRequestException;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.lang.System.Logger.Level.WARNING;

/// Adapts a [WebhookClient] to the provider-neutral [IncidentClient]: reporting an
/// incident POSTs the alert rendered by the configured [WebhookFormat].
///
/// A webhook message is fire-and-forget notification, not incident management — there is
/// no incident id, permalink, or lifecycle. Direct reporting echoes the original alert
/// key in [IncidentResponse#key()]. Reporting through
/// [software.sava.incident.core.api.IncidentService] replaces a null or blank key once
/// per logical report, keeping the generated key stable across retries. It appears in
/// the generic JSON `key` field, the Slack/Telegram `Key:` line, and the response key.
/// The Telegram key line counts toward its 4096-character text limit and may be
/// truncated with the rest of the message.
///
/// [IncidentResponse#status()] is the receiver's stripped response body (`ok` for Slack)
/// or `delivered` when the body is empty, and [IncidentResponse#url()] is null. For Telegram
/// it is the Bot API's success document, `{"ok":true,"result":{...}}`, whose `result` is the
/// sent Message: its `message_id` (0 when Telegram schedules the message instead of sending it
/// at once) and `chat.id`, the chat the alert reached — the new supergroup when an upgraded
/// group was followed. It echoes the message text, so it is about as long as the alert.
/// [IncidentClient#supportsResolve()] is false and [IncidentClient#resolveIncident(String)]
/// fails the returned future with an UnsupportedOperationException.
///
/// When the receiver refuses a report and the refusal cannot be retried (a
/// [WebhookRequestException] whose `canBeRetried()` is false), the format is asked whether the
/// response body names a destination in its place ([WebhookFormat#movedTo(String)]). When it
/// names a format other than its own, the alert is rendered with that format and sent once
/// more, a WARNING naming both formats, as their `toString()` gives them, is logged to the
/// `software.sava.incident.webhook` logger, and the report completes with that second
/// request's outcome: its response, or its failure with the first refusal suppressed in it;
/// when rendering with the moved format or posting throws a `RuntimeException`, or the client
/// returns no future, the report fails with the first refusal, what was thrown suppressed in
/// it. A throwable is never suppressed in itself: a second request that fails with the very
/// refusal it followed leaves that refusal as it is.
/// Nothing is remembered, so every report
/// tries the configured format first, and logs again, until the configuration is updated;
/// the second request's answer is never asked about, so a report makes at most two
/// requests. Any other failure is the one the receiver gave.
public final class WebhookIncidentClient implements IncidentClient {

  /// Fallback [IncidentResponse#status()] for an empty 2xx response body.
  static final String DELIVERED = "delivered";

  private static final System.Logger log = System.getLogger(WebhookIncidentClient.class.getPackageName());

  public static IncidentClient createClient(final WebhookClient client, final WebhookFormat format) {
    return new WebhookIncidentClient(
        Objects.requireNonNull(client, "'client' is required."),
        Objects.requireNonNull(format, "'format' is required.")
    );
  }

  private final WebhookClient client;
  private final WebhookFormat format;

  private WebhookIncidentClient(final WebhookClient client, final WebhookFormat format) {
    this.client = client;
    this.format = format;
  }

  WebhookFormat format() {
    return format;
  }

  @Override
  public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert) {
    // a render that throws still throws here, before anything is sent
    final var first = client.post(format.render(alert), format);
    return first.exceptionallyCompose(failure -> follow(alert, failure, first)).thenApply(responseBody ->
        IncidentResponse.of(
            alert.key(),
            responseBody.isBlank() ? DELIVERED : responseBody.strip(),
            null
        )
    );
  }

  /// `first` itself, which failed with `failure`, unless that is a refusal that cannot be
  /// retried and the format names another in its place: then the alert rendered with the
  /// moved format, sent once. Returning `first` keeps an unfollowed failure the throwable it
  /// was, at the depth a dependent stage has always seen it.
  private CompletableFuture<String> follow(final IncidentAlert alert,
                                           final Throwable failure,
                                           final CompletableFuture<String> first) {
    if (!(unwrap(failure) instanceof final WebhookRequestException refused) || refused.canBeRetried()) {
      return first;
    }
    final WebhookFormat moved;
    try {
      moved = format.movedTo(refused.body());
      if (moved == null || moved.equals(format)) {
        return first;
      }
    } catch (final RuntimeException faultyFormat) {
      return first;
    }
    final CompletableFuture<String> resend;
    try {
      final var body = moved.render(alert);
      // the client, which holds the endpoint and its credential, is not printed; the formats
      // are, as their toString() gives them, and the built-in ones carry neither
      log.log(WARNING, "Webhook receiver refused " + format + " with status " + refused.errorCode()
          + " and named " + moved + " in its place; sending the alert once more with it."
          + " Update the configuration to match.");
      resend = Objects.requireNonNull(client.post(body, moved), "The client returned no future for the re-send.");
    } catch (final RuntimeException unsent) {
      // a throwable cannot suppress itself
      if (unsent != refused) {
        refused.addSuppressed(unsent);
      }
      return first;
    }
    return resend.whenComplete((_, resendFailure) -> {
      if (resendFailure != null) {
        final var resendCause = unwrap(resendFailure);
        if (resendCause != refused) {
          resendCause.addSuppressed(refused);
        }
      }
    });
  }

  /// The cause a [CompletionException] carries, or `failure` itself.
  private static Throwable unwrap(final Throwable failure) {
    return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
  }

  @Override
  public boolean supportsResolve() {
    return false;
  }

  @Override
  public CompletableFuture<IncidentResponse> resolveIncident(final String key) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException(
        "A webhook message has no incident lifecycle; resolve is not supported."
    ));
  }

  @Override
  public URI endpoint() {
    return client.endpoint();
  }

  @Override
  public HttpClient httpClient() {
    return client.httpClient();
  }

  @Override
  public String toString() {
    return "WebhookIncidentClient{format=" + format + ", client=" + client + '}';
  }
}
