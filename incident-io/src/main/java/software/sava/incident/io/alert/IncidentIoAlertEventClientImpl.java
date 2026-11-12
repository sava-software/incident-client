package software.sava.incident.io.alert;

import software.sava.incident.io.exceptions.IncidentIoParseException;
import software.sava.incident.io.exceptions.IncidentIoRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

final class IncidentIoAlertEventClientImpl extends JsonHttpClient implements IncidentIoAlertEventClient {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private static final Function<HttpResponse<?>, AlertEventResponse> RESPONSE_PARSER =
      httpResponse -> {
        final byte[] body = readBody(httpResponse);
        final int statusCode = httpResponse.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
          throw IncidentIoRequestException.parse(httpResponse, body);
        }
        try {
          return AlertEventResponseRecord.parse(JsonIterator.parse(body));
        } catch (final RuntimeException parseCause) {
          throw new IncidentIoParseException(httpResponse,
              String.format("Failed to adapt %d response: '%s'", statusCode, new String(body)),
              parseCause
          );
        }
      };

  IncidentIoAlertEventClientImpl(final URI endpoint,
                                 final HttpClient httpClient,
                                 final Duration requestTimeout,
                                 final UnaryOperator<HttpRequest.Builder> extendRequest,
                                 final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
    super(endpoint, httpClient, requestTimeout, extendRequest, testResponse);
  }

  /// `segment`'s UTF-8 bytes as one RFC 3986 path segment: every byte other than ALPHA,
  /// DIGIT, `-`, `.`, `_` and `~` becomes `%XX` in uppercase hex. Unlike `URLEncoder`, a
  /// space is `%20`, never `+`.
  static String encodePathSegment(final String segment) {
    final byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
    final var encoded = new StringBuilder(bytes.length);
    for (final byte b : bytes) {
      final int c = b & 0xFF;
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
          || c == '-' || c == '.' || c == '_' || c == '~') {
        encoded.append((char) c);
      } else {
        encoded.append('%').append(HEX[c >>> 4]).append(HEX[c & 0xF]);
      }
    }
    return encoded.toString();
  }

  @Override
  public CompletableFuture<AlertEventResponse> sendAlertEvent(final AlertEvent event) {
    Objects.requireNonNull(event, "'event' is required.");
    return sendPostRequest(RESPONSE_PARSER, event.body());
  }
}
