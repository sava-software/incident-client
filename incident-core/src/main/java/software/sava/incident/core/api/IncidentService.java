package software.sava.incident.core.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

/// Retrying wrapper around any [IncidentClient]. Failures whose cause implements
/// [IncidentClientException] are retried only when [IncidentClientException#canBeRetried()]
/// holds; transport-level failures are always retried. A retry-delay function maps the
/// number of failures so far to the next delay, or a negative value to give up.
public interface IncidentService {

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
