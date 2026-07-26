package software.sava.incident.io.exceptions;

import software.sava.incident.core.api.IncidentClientException;
import systems.comodal.jsoniter.FieldIndexPredicate;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// A non-2xx incident.io response, carrying the parsed error envelope:
/// `{"type": ..., "status": ..., "request_id": ..., "errors": [{"code": ..., "message": ...}]}`
public final class IncidentIoRequestException extends RuntimeException implements IncidentClientException {

  private final String type;
  private final String requestId;
  private final long errorCode;
  private final List<String> errors;
  private final HttpResponse<?> httpResponse;

  private IncidentIoRequestException(final String message,
                                     final String type,
                                     final String requestId,
                                     final long errorCode,
                                     final List<String> errors,
                                     final HttpResponse<?> httpResponse) {
    super(message);
    this.type = type;
    this.requestId = requestId;
    this.errorCode = errorCode;
    this.errors = errors;
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

    static final FieldMatcher FIELDS = FieldMatcher.of("type", "status", "request_id", "errors");
    private static final FieldMatcher ERROR_FIELDS = FieldMatcher.of("code", "message");

    private String type;
    private String requestId;
    private long errorCode;
    private List<String> errors;

    private IncidentIoRequestException create(final HttpResponse<?> httpResponse) {
      final var errors = this.errors == null
          ? List.<String>of()
          : this.errors.size() > 1 ? Collections.unmodifiableList(this.errors) : this.errors;
      final var message = errors.isEmpty() ? type : String.join("; ", errors);
      return new IncidentIoRequestException(message, type, requestId, errorCode, errors, httpResponse);
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
            };
            ji.testObject(ERROR_FIELDS, (errorField, ji2) -> {
              switch (errorField) {
                case 0 -> error.code = ji2.readString();
                case 1 -> error.message = ji2.readString();
                default -> ji2.skip();
              }
              return true;
            });
            if (error.code == null) {
              addError(error.message == null ? "unknown error" : error.message);
            } else if (error.message == null) {
              addError(error.code);
            } else {
              addError(error.code + ": " + error.message);
            }
          }
        }
        default -> ji.skip();
      }
      return true;
    }
  }
}
