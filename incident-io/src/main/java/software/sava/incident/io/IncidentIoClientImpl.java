package software.sava.incident.io;

import software.sava.incident.io.exceptions.IncidentIoParseException;
import software.sava.incident.io.exceptions.IncidentIoRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNullElse;

final class IncidentIoClientImpl extends JsonHttpClient implements IncidentIoClient {

  private static final Function<HttpResponse<?>, CreateIncidentResponse> CREATE_INCIDENT_RESPONSE_PARSER =
      httpResponse -> {
        final byte[] body = readBody(httpResponse);
        final int statusCode = httpResponse.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
          throw IncidentIoRequestException.parse(httpResponse, body);
        }
        try {
          return CreateIncidentResponseRecord.parse(JsonIterator.parse(body));
        } catch (final RuntimeException parseCause) {
          throw new IncidentIoParseException(httpResponse,
              String.format("Failed to adapt %d response: '%s'", statusCode, new String(body)),
              parseCause
          );
        }
      };

  IncidentIoClientImpl(final URI endpoint,
                       final HttpClient httpClient,
                       final Duration requestTimeout,
                       final UnaryOperator<HttpRequest.Builder> extendRequest,
                       final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
    super(endpoint, httpClient, requestTimeout, extendRequest, testResponse);
  }

  @Override
  public CompletableFuture<CreateIncidentResponse> createIncident(final CreateIncidentRequest request) {
    final var body = request.body();
    return sendPostRequest(
        CREATE_INCIDENT_RESPONSE_PARSER,
        requireNonNullElse(request.timeout(), this.requestTimeout),
        body
    );
  }
}
