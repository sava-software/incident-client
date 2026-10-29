package software.sava.incident.core.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

/// Retrying wrapper around any [IncidentClient]. Failures whose cause implements
/// [IncidentClientException] are retried only when [IncidentClientException#canBeRetried()]
/// holds; transport-level failures are always retried. A retry-delay function maps the
/// number of failures so far to the next delay, or a negative value to give up.
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

  /// Refuses a client whose [IncidentClient#maxKeyLength()] is under the 64-character digest.
  static IncidentService createService(final IncidentClient client) {
    return new IncidentServiceVal(client);
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
