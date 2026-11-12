package software.sava.incident.core.api;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

record IncidentServiceVal(IncidentClient client,
                          Duration maxRetryAfter,
                          DelayedExecutorFactory delayedExecutors) implements IncidentService {

  /// The seam behind every delay the service serves, so tests can record the requests and run
  /// the work inline instead of waiting.
  @FunctionalInterface
  interface DelayedExecutorFactory {

    Executor delayedExecutor(long delay, TimeUnit unit);
  }

  /// One shared instance, so two services for the same client and limit stay equal.
  static final DelayedExecutorFactory DELAYED_EXECUTOR = CompletableFuture::delayedExecutor;

  /// A client declaring a limit the shortening refuses, one under the digest, is refused here,
  /// before its first report would be.
  IncidentServiceVal {
    AlertKeys.checkLimit(requireNonNull(client, "client").maxKeyLength());
    requireNonNull(maxRetryAfter, "maxRetryAfter");
    if (maxRetryAfter.isNegative()) {
      throw new IllegalArgumentException("maxRetryAfter must not be negative: " + maxRetryAfter);
    }
  }

  private static final System.Logger log = System.getLogger(IncidentService.class.getPackageName());

  private static void logFailure(final Throwable throwable,
                                 final int numFailures,
                                 final long retryDelay,
                                 final TimeUnit timeUnit,
                                 final String context,
                                 final String retryAfter) {
    if (throwable.getCause() instanceof final IncidentClientException clientException) {
      log.log(ERROR, format("Http Error Code: %s, Service Error Code: %s, Failure Count: %d, Last Delay: %d %s, Service Errors: %s, %s%s",
              clientException.httpResponse() == null ? "?" : String.valueOf(clientException.httpResponse().statusCode()),
              clientException.errorCode() == 0 ? "?" : String.valueOf(clientException.errorCode()),
              numFailures,
              retryDelay, timeUnit,
              clientException.errors().toString(),
              context, retryAfter
          ), throwable.getCause()
      );
    } else {
      log.log(ERROR, format("Failure Count: %d, Last Delay: %d %s, %s%s",
              numFailures, retryDelay, timeUnit, context, retryAfter
          ), throwable.getCause()
      );
    }
  }

  private static boolean canBeRetried(final Throwable throwable) {
    return switch (throwable) {
      case IncidentClientException ex -> ex.canBeRetried();
      default -> !(throwable.getCause() instanceof final IncidentClientException ex) || ex.canBeRetried();
    };
  }

  /// The wait the failure's provider asked for, from the failure itself or else its cause; null
  /// when none is positive, including when asking throws.
  private static Duration serverWait(final Throwable throwable) {
    final IncidentClientException clientException;
    if (throwable instanceof final IncidentClientException ex) {
      clientException = ex;
    } else if (throwable.getCause() instanceof final IncidentClientException ex) {
      clientException = ex;
    } else {
      return null;
    }
    final Duration wait;
    try {
      wait = clientException.retryAfter();
    } catch (final RuntimeException unreadable) {
      return null;
    }
    return wait != null && wait.isPositive() ? wait : null;
  }

  /// `wait` in `timeUnit`, rounded up; saturates at [Long#MAX_VALUE] and never throws.
  static long roundUp(final Duration wait, final TimeUnit timeUnit) {
    final long converted = timeUnit.convert(wait);
    return converted < Long.MAX_VALUE && Duration.of(converted, timeUnit.toChronoUnit()).compareTo(wait) < 0
        ? converted + 1
        : converted;
  }

  /// The failure as the returned future surfaces it when no further attempt is made.
  private static RuntimeException terminal(final Throwable throwable) {
    if (throwable instanceof final RuntimeException runtimeException) {
      return runtimeException;
    } else if (throwable.getCause() instanceof final RuntimeException runtimeException) {
      return runtimeException;
    } else {
      return new RuntimeException(requireNonNullElse(throwable.getCause(), throwable));
    }
  }

  private CompletableFuture<IncidentResponse> retry(final Supplier<CompletableFuture<IncidentResponse>> call,
                                                    final Supplier<String> context,
                                                    final LongUnaryOperator retryDelayFn,
                                                    final TimeUnit timeUnit) {
    final long retryDelay = retryDelayFn.applyAsLong(0);
    if (retryDelay < 0) {
      return null;
    }
    return attempt(call, context, 0, retryDelay, retryDelayFn, timeUnit);
  }

  /// Makes attempt `retry` at once; if it fails, its handler runs after `retryDelay`, the
  /// retry-delay function's value for this attempt, already evaluated.
  private CompletableFuture<IncidentResponse> attempt(final Supplier<CompletableFuture<IncidentResponse>> call,
                                                      final Supplier<String> context,
                                                      final int retry,
                                                      final long retryDelay,
                                                      final LongUnaryOperator retryDelayFn,
                                                      final TimeUnit timeUnit) {
    final var responseFuture = call.get();
    final Function<Throwable, CompletableFuture<IncidentResponse>> exceptionally = throwable -> {
      final int numFailures = retry + 1;
      final var serverWait = serverWait(throwable);
      final long serverDelay = serverWait == null ? 0 : roundUp(serverWait, timeUnit);
      logFailure(throwable, numFailures, retryDelay, timeUnit, context.get(),
          serverWait == null ? "" : format(", Retry-After: %d %s", serverDelay, timeUnit));
      if (!canBeRetried(throwable)) {
        throw terminal(throwable);
      }
      final long nextDelay = retryDelayFn.applyAsLong(numFailures);
      if (nextDelay < 0) {
        return null;
      }
      if (serverWait != null && serverWait.compareTo(maxRetryAfter) > 0) {
        throw terminal(throwable);
      }
      if (serverDelay > retryDelay) {
        // the handler already ran retryDelay after the failure
        return CompletableFuture.supplyAsync(
            () -> attempt(call, context, numFailures, nextDelay, retryDelayFn, timeUnit),
            delayedExecutors.delayedExecutor(serverDelay - retryDelay, timeUnit)
        ).thenCompose(Function.identity());
      } else {
        return attempt(call, context, numFailures, nextDelay, retryDelayFn, timeUnit);
      }
    };
    if (retryDelay > 0) {
      return responseFuture.exceptionallyComposeAsync(exceptionally, delayedExecutors.delayedExecutor(retryDelay, timeUnit));
    } else {
      return responseFuture.exceptionallyCompose(exceptionally);
    }
  }

  @Override
  public CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert,
                                                            final LongUnaryOperator retryDelayFn,
                                                            final TimeUnit timeUnit) {
    final var key = alert.key();
    final var reportKey = IncidentClient.boundKey(key == null || key.isBlank() ? UUID.randomUUID().toString() : key, client.maxKeyLength());
    final boolean sentAsIs = reportKey.equals(key);
    final var reportAlert = sentAsIs ? alert : new KeyedIncidentAlert(alert, reportKey);
    return retry(
        () -> client.reportIncident(reportAlert),
        () -> sentAsIs
            ? format("to report incident:%n  %s", alert)
            : format("to report incident, sent with key '%s':%n  %s", reportKey, alert),
        retryDelayFn, timeUnit
    );
  }

  @Override
  public CompletableFuture<IncidentResponse> resolveIncident(final String key,
                                                             final LongUnaryOperator retryDelayFn,
                                                             final TimeUnit timeUnit) {
    if (key == null) {
      return null;
    }
    // the key as the provider knows it: shortened the same way the report was
    final var resolveKey = IncidentClient.boundKey(key, client.maxKeyLength());
    if (!client.supportsResolve()) {
      return client.resolveIncident(resolveKey);
    }
    final boolean sentAsIs = resolveKey.equals(key);
    return retry(
        () -> client.resolveIncident(resolveKey),
        () -> sentAsIs
            ? format("to resolve incident with key '%s'.", key)
            : format("to resolve incident with key '%s', sent as '%s'.", key, resolveKey),
        retryDelayFn, timeUnit
    );
  }

  /// Today's text: the limit and the executor seam are not part of it.
  @Override
  public String toString() {
    return "IncidentServiceVal[client=" + client + ']';
  }
}
