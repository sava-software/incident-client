package software.sava.incident.pagerduty.event.service;

import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

public interface PagerDutyService {

  static Builder build() {
    return new PagerDutyServiceBuilder();
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

  PagerDutyEventClient client();

  PagerDutyEventPayload eventPrototype();

  default CompletableFuture<PagerDutyEventResponse> resolveEvent(final PagerDutyEventResponse triggerResponse,
                                                                 final long stepDelay,
                                                                 final long maxDelay,
                                                                 final TimeUnit timeUnit) {
    return triggerResponse == null ? null : resolveEvent(triggerResponse.dedupKey(), stepDelay, maxDelay, timeUnit);
  }

  CompletableFuture<PagerDutyEventResponse> resolveEvent(final String dedupeKey,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  default CompletableFuture<PagerDutyEventResponse> resolveEvent(final PagerDutyEventResponse triggerResponse,
                                                                 final Duration giveUpAfter,
                                                                 final long stepDelay,
                                                                 final long maxDelay,
                                                                 final TimeUnit timeUnit) {
    return triggerResponse == null ? null : resolveEvent(triggerResponse.dedupKey(), giveUpAfter, stepDelay, maxDelay, timeUnit);
  }

  CompletableFuture<PagerDutyEventResponse> resolveEvent(final String dedupeKey,
                                                         final Duration giveUpAfter,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  default CompletableFuture<PagerDutyEventResponse> resolveEvent(final PagerDutyEventResponse triggerResponse,
                                                                 final int maxRetries,
                                                                 final long stepDelay,
                                                                 final long maxDelay,
                                                                 final TimeUnit timeUnit) {
    return triggerResponse == null ? null : resolveEvent(triggerResponse.dedupKey(), maxRetries, stepDelay, maxDelay, timeUnit);
  }

  CompletableFuture<PagerDutyEventResponse> resolveEvent(final String dedupeKey,
                                                         final int maxRetries,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  default CompletableFuture<PagerDutyEventResponse> resolveEvent(final PagerDutyEventResponse triggerResponse,
                                                                 final LongUnaryOperator retryDelayFn,
                                                                 final TimeUnit timeUnit) {
    return triggerResponse == null ? null : resolveEvent(triggerResponse.dedupKey(), retryDelayFn, timeUnit);
  }

  CompletableFuture<PagerDutyEventResponse> resolveEvent(final String dedupeKey,
                                                         final LongUnaryOperator retryDelayFn,
                                                         final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> triggerEvent(final PagerDutyEventPayload payload,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> triggerEvent(final PagerDutyEventPayload payload,
                                                         final Duration giveUpAfter,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> triggerEvent(final PagerDutyEventPayload payload,
                                                         final int maxRetries,
                                                         final long stepDelay,
                                                         final long maxDelay,
                                                         final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> triggerEvent(final PagerDutyEventPayload payload,
                                                         final LongUnaryOperator retryDelayFn,
                                                         final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> changeEvent(final PagerDutyChangeEventPayload payload,
                                                        final long stepDelay,
                                                        final long maxDelay,
                                                        final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> changeEvent(final PagerDutyChangeEventPayload payload,
                                                        final Duration giveUpAfter,
                                                        final long stepDelay,
                                                        final long maxDelay,
                                                        final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> changeEvent(final PagerDutyChangeEventPayload payload,
                                                        final int maxRetries,
                                                        final long stepDelay,
                                                        final long maxDelay,
                                                        final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> changeEvent(final PagerDutyChangeEventPayload payload,
                                                        final LongUnaryOperator retryDelayFn,
                                                        final TimeUnit timeUnit);

  CompletableFuture<PagerDutyEventResponse> changeEvent(final PagerDutyChangeEventPayload payload,
                                                        final int retry,
                                                        final LongUnaryOperator retryDelayFn,
                                                        final TimeUnit timeUnit);

  interface Builder {

    PagerDutyService create();

    Builder client(final PagerDutyEventClient client);

    Builder eventPrototype(final PagerDutyEventPayload eventPrototype);
  }
}
