package software.sava.incident.io.exceptions;

import software.sava.incident.core.api.IncidentClientException;
import systems.comodal.jsoniter.FieldIndexPredicate;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A non-2xx incident.io response, carrying the error envelope and optional structured
/// diagnostics. [#errors()] exposes readable messages; [#errorDetails()] also preserves
/// each error's code, metadata, and source location.
public final class IncidentIoRequestException extends RuntimeException implements IncidentClientException {

  private final String type;
  private final String requestId;
  private final long errorCode;
  private final List<String> errors;
  private final List<ErrorDetail> errorDetails;
  private final RateLimit rateLimit;
  private final DebugInfo debug;
  private final HttpResponse<?> httpResponse;

  private IncidentIoRequestException(final String message,
                                     final String type,
                                     final String requestId,
                                     final long errorCode,
                                     final List<String> errors,
                                     final List<ErrorDetail> errorDetails,
                                     final RateLimit rateLimit,
                                     final DebugInfo debug,
                                     final HttpResponse<?> httpResponse) {
    super(message);
    this.type = type;
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.errors = errors;
    this.errorDetails = errorDetails;
    this.rateLimit = rateLimit;
    this.debug = debug;
    this.httpResponse = httpResponse;
  }

  public static IncidentIoRequestException parse(final HttpResponse<?> httpResponse, final byte[] body) {
    final var parser = new Parser();
    try {
      JsonIterator.parse(body).testObject(Parser.FIELDS, parser);
    } catch (final RuntimeException parseCause) {
      throw new IncidentIoParseException(httpResponse,
          String.format("Failed to adapt %d error response: '%s'",
              httpResponse == null ? -1 : httpResponse.statusCode(), new String(body)
          ),
          parseCause
      );
    }
    return parser.create(httpResponse);
  }

  @Override
  public boolean canBeRetried() {
    return httpResponse == null
        || httpResponse.statusCode() >= 500
        || httpResponse.statusCode() == 429;
  }

  @Override
  public HttpResponse<?> httpResponse() {
    return httpResponse;
  }

  /// The `status` field of the error envelope, typically echoing the HTTP status code.
  @Override
  public long errorCode() {
    return errorCode;
  }

  @Override
  public List<String> errors() {
    return errors;
  }

  /// Structured errors in response order. [#errors()] exposes a readable description
  /// of each entry (`code: message` when both are present).
  public List<ErrorDetail> errorDetails() {
    return errorDetails;
  }

  /// Rate-limit information, or null when the response omits it.
  public RateLimit rateLimit() {
    return rateLimit;
  }

  /// Internal diagnostics supplied by the server, or null when absent.
  public DebugInfo debug() {
    return debug;
  }

  /// An `ErrorSingle`. Missing metadata is an empty immutable map; `source` is optional.
  /// Metadata keys and values must be non-null.
  public record ErrorDetail(String code, String message, Map<String, String> metadata, ErrorSource source) {

    public ErrorDetail {
      if (metadata == null) {
        metadata = Map.of();
      } else {
        // HashMap treeifies colliding String keys; Map.copyOf's compact map probes linearly.
        final var copy = new HashMap<>(metadata);
        copy.forEach((key, value) -> {
          Objects.requireNonNull(key, "metadata key");
          Objects.requireNonNull(value, "metadata value");
        });
        metadata = Collections.unmodifiableMap(copy);
      }
    }
  }

  /// An `ErrorSource`: the field name and JSON pointer identifying the invalid input.
  /// The parser tolerates an omitted member and represents it as null.
  public record ErrorSource(String field, String pointer) {
  }

  /// An `ErrorRateLimit`. `retryAfter` is the server's RFC 3339 UTC timestamp, preserved
  /// as a string as specified by the schema. Prefer the `Retry-After` response header
  /// (seconds until retry), available through [IncidentIoRequestException#httpResponse()],
  /// when scheduling retries.
  public record RateLimit(String name, long limit, long remaining, String retryAfter) {
  }

  /// An `ErrorDebug`. Missing stack frames are an empty immutable list; null elements
  /// within a supplied list are rejected.
  public record DebugInfo(String message, List<String> stacktrace) {

    public DebugInfo {
      stacktrace = stacktrace == null ? List.of() : List.copyOf(stacktrace);
    }
  }

  public String type() {
    return type;
  }

  public String requestId() {
    return requestId;
  }

  @Override
  public String toString() {
    return "IncidentIoRequestException{type='" + type + '\'' +
        ", requestId='" + requestId + '\'' +
        ", errorCode=" + errorCode +
        ", errors=" + errors +
        ", httpResponse=" + httpResponse + '}';
  }

  private static final class Parser implements FieldIndexPredicate {

    static final FieldMatcher FIELDS = FieldMatcher.of("type", "status", "request_id", "errors", "rate_limit", "debug");
    private static final FieldMatcher ERROR_FIELDS = FieldMatcher.of("code", "message", "metadata", "source");
    private static final FieldMatcher SOURCE_FIELDS = FieldMatcher.of("field", "pointer");
    private static final FieldMatcher RATE_LIMIT_FIELDS = FieldMatcher.of("name", "limit", "remaining", "retry_after");
    private static final FieldMatcher DEBUG_FIELDS = FieldMatcher.of("message", "stacktrace");

    private String type;
    private String requestId;
    private long errorCode;
    private List<String> errors;
    private final List<ErrorDetail> errorDetails = new ArrayList<>();
    private RateLimit rateLimit;
    private DebugInfo debug;

    private IncidentIoRequestException create(final HttpResponse<?> httpResponse) {
      final var errors = this.errors == null
          ? List.<String>of()
          : this.errors.size() > 1 ? Collections.unmodifiableList(this.errors) : this.errors;
      final var message = errors.isEmpty() ? type : String.join("; ", errors);
      return new IncidentIoRequestException(message, type, requestId, errorCode, errors,
          List.copyOf(errorDetails), rateLimit, debug, httpResponse);
    }

    private void addError(final String error) {
      if (errors == null) {
        errors = List.of(error);
        return;
      }
      if (errors.size() == 1) {
        final var move = errors.getFirst();
        errors = new ArrayList<>(10);
        errors.add(move);
      }
      errors.add(error);
    }

    @Override
    public boolean test(final int fieldIndex, final JsonIterator ji) {
      switch (fieldIndex) {
        case 0 -> this.type = ji.readString();
        case 1 -> this.errorCode = ji.readLong();
        case 2 -> this.requestId = ji.readString();
        case 3 -> {
          while (ji.readArray()) {
            final var error = new Object() {
              String code, message;
              Map<String, String> metadata;
              ErrorSource source;
            };
            ji.testObject(ERROR_FIELDS, (errorField, ji2) -> {
              switch (errorField) {
                case 0 -> error.code = ji2.readString();
                case 1 -> error.message = ji2.readString();
                case 2 -> error.metadata = ji2.readMap(String::new, (_, value) -> value.readString());
                case 3 -> error.source = parseSource(ji2);
                default -> ji2.skip();
              }
              return true;
            });
            errorDetails.add(new ErrorDetail(error.code, error.message, error.metadata, error.source));
            if (error.code == null) {
              addError(error.message == null ? "unknown error" : error.message);
            } else if (error.message == null) {
              addError(error.code);
            } else {
              addError(error.code + ": " + error.message);
            }
          }
        }
        case 4 -> rateLimit = parseRateLimit(ji);
        case 5 -> debug = parseDebug(ji);
        default -> ji.skip();
      }
      return true;
    }

    private static ErrorSource parseSource(final JsonIterator ji) {
      if (!ji.notNull()) {
        return null;
      }
      final var source = new Object() {
        String field, pointer;
      };
      ji.testObject(SOURCE_FIELDS, (field, value) -> {
        switch (field) {
          case 0 -> source.field = value.readString();
          case 1 -> source.pointer = value.readString();
          default -> value.skip();
        }
        return true;
      });
      return new ErrorSource(source.field, source.pointer);
    }

    private static RateLimit parseRateLimit(final JsonIterator ji) {
      if (!ji.notNull()) {
        return null;
      }
      final var rateLimit = new Object() {
        String name, retryAfter;
        long limit, remaining;
      };
      ji.testObject(RATE_LIMIT_FIELDS, (field, value) -> {
        switch (field) {
          case 0 -> rateLimit.name = value.readString();
          case 1 -> rateLimit.limit = value.readLong();
          case 2 -> rateLimit.remaining = value.readLong();
          case 3 -> rateLimit.retryAfter = value.readString();
          default -> value.skip();
        }
        return true;
      });
      return new RateLimit(rateLimit.name, rateLimit.limit, rateLimit.remaining, rateLimit.retryAfter);
    }

    private static DebugInfo parseDebug(final JsonIterator ji) {
      if (!ji.notNull()) {
        return null;
      }
      final var debug = new Object() {
        String message;
        List<String> stacktrace;
      };
      ji.testObject(DEBUG_FIELDS, (field, value) -> {
        switch (field) {
          case 0 -> debug.message = value.readString();
          case 1 -> debug.stacktrace = value.readList(JsonIterator::readString);
          default -> value.skip();
        }
        return true;
      });
      return new DebugInfo(debug.message, debug.stacktrace);
    }
  }
}
