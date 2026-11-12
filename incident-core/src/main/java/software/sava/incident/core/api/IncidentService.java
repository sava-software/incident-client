package software.sava.incident.core.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

/// Retrying wrapper around any [IncidentClient]. Failures whose cause implements
/// [IncidentClientException] are retried only when [IncidentClientException#canBeRetried()]
/// holds; transport-level failures are always retried.
///
/// A retry-delay function maps an attempt number, counting from 0, to a delay in the call's
/// [TimeUnit], or to a negative value to give up. It is applied once per attempt, before the
/// attempt is made: attempt n is made at once, and if it fails, the value for n is served
/// before the failure is handled and attempt n + 1 begins. A negative value for n + 1 gives
/// up and fails the returned future with attempt n's failure, as a failure that cannot be
/// retried fails it; a negative value for 0 makes no attempt and returns null.
///
/// Whenever no further attempt is made, the returned future fails with the last attempt's
/// failure: a [RuntimeException] as is (including the [java.util.concurrent.CompletionException]
/// an upstream stage wrapped it in), else its cause when that is a [RuntimeException], else a
/// [RuntimeException] wrapping the cause, or the failure itself when it has none.
///
/// A provider that asks for a wait before the next attempt
/// ([IncidentClientException#retryAfter()], such as a 429's `Retry-After`) has it waited
/// out, rounded up to the call's unit, before the next attempt: whatever part of it the
/// retry-delay function's value did not already cover. A wait longer than the service's
/// limit is not served: the call ends at once with the provider's failure, whatever the
/// retry count, so the caller can fall back rather than hold an alert. The limit belongs to
/// the service and applies to every overload; it is [#DEFAULT_MAX_RETRY_AFTER] for
/// [#createService(IncidentClient)], and set with
/// [#createService(IncidentClient, Duration)]. The `giveUpAfter` overloads only derive a
/// retry count from the step delay: they are not a timeout, and a provider's wait is not
/// counted against them.
///
/// A report with a null or blank alert key receives a generated key once per service
/// call, shared by all attempts. A key longer than the client's
/// [IncidentClient#maxKeyLength()] is shortened the same way on every report and resolve
/// (its first characters, a colon and the SHA-256 of the whole key), so the provider never
/// sees one past the limit the client declares, in characters as Java counts them. The
/// caller's alert is unchanged; a separate report call gets a
/// new key. Where the key is generated or shortened the client receives a forwarding view
/// that delegates all non-key accessors and `toString()` to the original alert. Explicit
/// keys within the limit preserve the original alert instance; supply one if a custom
/// client depends on its concrete type.
/// Unsupported resolution is delegated once to the client without retries.
public interface IncidentService {

  /// The longest provider-requested wait [#createService(IncidentClient)] serves: one minute.
  Duration DEFAULT_MAX_RETRY_AFTER = Duration.ofMinutes(1);

  /// Refuses a client whose [IncidentClient#maxKeyLength()] is under the 64-character digest.
  /// Serves a provider-requested wait up to [#DEFAULT_MAX_RETRY_AFTER].
  static IncidentService createService(final IncidentClient client) {
    return createService(client, DEFAULT_MAX_RETRY_AFTER);
  }

  /// Refuses a client whose [IncidentClient#maxKeyLength()] is under the 64-character digest.
  ///
  /// @param maxRetryAfter the longest provider-requested wait this service serves before its
  ///                      next attempt; a longer one ends the call at once with the provider's
  ///                      failure. [Duration#ZERO] refuses every wait.
  /// @throws NullPointerException     if `maxRetryAfter` is null
  /// @throws IllegalArgumentException if `maxRetryAfter` is negative
  static IncidentService createService(final IncidentClient client, final Duration maxRetryAfter) {
    return new IncidentServiceVal(client, maxRetryAfter, IncidentServiceVal.DELAYED_EXECUTOR);
  }

  static LongUnaryOperator createRetryDelayFn(final long stepDelay, final long maxDelay) {
    return numFailures -> Math.min(maxDelay, numFailures * stepDelay);
  }

  static LongUnaryOperator createRetryDelayFn(final int maxRetries,
                                              final long stepDelay,
                                              final long maxDelay) {
    return numFailures -> numFailures > maxRetries
        ? Long.MIN_VALUE
        : Math.min(maxDelay, numFailures * stepDelay);
  }

  IncidentClient client();

  default CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert,
                                                             final long stepDelay,
                                                             final long maxDelay,
                                                             final TimeUnit timeUnit) {
    return reportIncident(alert, createRetryDelayFn(stepDelay, maxDelay), timeUnit);
  }

  default CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert,
                                                             final int maxRetries,
                                                             final long stepDelay,
                                                             final long maxDelay,
                                                             final TimeUnit timeUnit) {
    return reportIncident(alert, createRetryDelayFn(maxRetries, stepDelay, maxDelay), timeUnit);
  }

  default CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert,
                                                             final Duration giveUpAfter,
                                                             final long stepDelay,
                                                             final long maxDelay,
                                                             final TimeUnit timeUnit) {
    final int maxRetries = (int) Math.min(Integer.MAX_VALUE, giveUpAfter.toMillis() / timeUnit.toMillis(stepDelay));
    return reportIncident(alert, createRetryDelayFn(maxRetries, stepDelay, maxDelay), timeUnit);
  }

  CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert,
                                                     final LongUnaryOperator retryDelayFn,
                                                     final TimeUnit timeUnit);

  default CompletableFuture<IncidentResponse> resolveIncident(final IncidentResponse reportResponse,
                                                              final long stepDelay,
                                                              final long maxDelay,
                                                              final TimeUnit timeUnit) {
    return reportResponse == null ? null
        : resolveIncident(reportResponse.key(), createRetryDelayFn(stepDelay, maxDelay), timeUnit);
  }

  default CompletableFuture<IncidentResponse> resolveIncident(final String key,
                                                              final long stepDelay,
                                                              final long maxDelay,
                                                              final TimeUnit timeUnit) {
    return resolveIncident(key, createRetryDelayFn(stepDelay, maxDelay), timeUnit);
  }

  default CompletableFuture<IncidentResponse> resolveIncident(final String key,
                                                              final int maxRetries,
                                                              final long stepDelay,
                                                              final long maxDelay,
                                                              final TimeUnit timeUnit) {
    return resolveIncident(key, createRetryDelayFn(maxRetries, stepDelay, maxDelay), timeUnit);
  }

  default CompletableFuture<IncidentResponse> resolveIncident(final String key,
                                                              final Duration giveUpAfter,
                                                              final long stepDelay,
                                                              final long maxDelay,
                                                              final TimeUnit timeUnit) {
    final int maxRetries = (int) Math.min(Integer.MAX_VALUE, giveUpAfter.toMillis() / timeUnit.toMillis(stepDelay));
    return resolveIncident(key, createRetryDelayFn(maxRetries, stepDelay, maxDelay), timeUnit);
  }

  CompletableFuture<IncidentResponse> resolveIncident(final String key,
                                                      final LongUnaryOperator retryDelayFn,
                                                      final TimeUnit timeUnit);
}
