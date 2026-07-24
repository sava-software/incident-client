package software.sava.incident.io.exceptions;

import software.sava.incident.core.api.IncidentClientException;

import java.net.http.HttpResponse;
import java.util.List;

/// A response body that could not be parsed — including error envelopes that themselves
/// failed to parse.
public final class IncidentIoParseException extends RuntimeException implements IncidentClientException {

  private final HttpResponse<?> httpResponse;

  public IncidentIoParseException(final HttpResponse<?> httpResponse, final String message, final Throwable cause) {
    super(message, cause);
    this.httpResponse = httpResponse;
  }

  public IncidentIoParseException(final HttpResponse<?> httpResponse, final String message) {
    super(message);
    this.httpResponse = httpResponse;
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

  @Override
  public long errorCode() {
    return 0;
  }

  @Override
  public List<String> errors() {
    return List.of();
  }
}
