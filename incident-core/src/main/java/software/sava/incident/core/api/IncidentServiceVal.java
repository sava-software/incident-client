package software.sava.incident.core.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.concurrent.CompletableFuture.delayedExecutor;

record IncidentServiceVal(IncidentClient client) implements IncidentService {

  /// A client declaring a limit the shortening refuses, one under the digest, is refused here,
  /// before its first report would be.
  IncidentServiceVal {
    AlertKeys.checkLimit(requireNonNull(client, "client").maxKeyLength());
  }

  private static final System.Logger log = System.getLogger(IncidentService.class.getPackageName());

  private static void logFailure(final Throwable throwable,
                                 final int numFailures,
                                 final long retryDelay,
                                 final TimeUnit timeUnit,
                                 final String context) {
    if (throwable.getCause() instanceof final IncidentClientException clientException) {
      log.log(ERROR, format("Http Error Code: %s, Service Error Code: %s, Failure Count: %d, Last Delay: %d %s, Service Errors: %s, %s",
              clientException.httpResponse() == null ? "?" : String.valueOf(clientException.httpResponse().statusCode()),
              clientException.errorCode() == 0 ? "?" : String.valueOf(clientException.errorCode()),
              numFailures,
              retryDelay, timeUnit,
              clientException.errors().toString(),
              context
          ), throwable.getCause()
      );
    } else {
      log.log(ERROR, format("Failure Count: %d, Last Delay: %d %s, %s",
              numFailures, retryDelay, timeUnit, context
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

  private CompletableFuture<IncidentResponse> retry(final Supplier<CompletableFuture<IncidentResponse>> call,
                                                    final Supplier<String> context,
                                                    final int retry,
                                                    final LongUnaryOperator retryDelayFn,
                                                    final TimeUnit timeUnit) {
    final long retryDelay = retryDelayFn.applyAsLong(retry);
    if (retryDelay < 0) {
      return null;
    }
    final var responseFuture = call.get();
    final Function<Throwable, CompletableFuture<IncidentResponse>> exceptionally = throwable -> {
      final int numFailures = retry + 1;
      logFailure(throwable, numFailures, retryDelay, timeUnit, context.get());
      if (canBeRetried(throwable)) {
        return retry(call, context, numFailures, retryDelayFn, timeUnit);
      } else if (throwable instanceof final RuntimeException runtimeException) {
        throw runtimeException;
      } else if (throwable.getCause() instanceof final RuntimeException runtimeException) {
        throw runtimeException;
      } else {
        throw new RuntimeException(requireNonNullElse(throwable.getCause(), throwable));
      }
    };
    if (retryDelay > 0) {
      return responseFuture.exceptionallyComposeAsync(exceptionally, delayedExecutor(retryDelay, timeUnit));
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
        0, retryDelayFn, timeUnit
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
        0, retryDelayFn, timeUnit
    );
  }
}
