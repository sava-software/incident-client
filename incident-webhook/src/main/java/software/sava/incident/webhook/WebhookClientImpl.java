package software.sava.incident.webhook;

import software.sava.incident.webhook.exceptions.WebhookRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.nio.charset.StandardCharsets.UTF_8;

final class WebhookClientImpl extends JsonHttpClient implements WebhookClient {

  private static final BiFunction<HttpResponse<?>, Function<String, Duration>, String> RESPONSE_PARSER =
    (httpResponse, bodyRetryAfter) -> {
      final byte[] body = readBody(httpResponse);
      // new String(byte[0]) is already ""; only the defensive null needs a branch
      final var text = body == null ? "" : new String(body, UTF_8);
      final int statusCode = httpResponse.statusCode();
      if (statusCode < 200 || statusCode >= 300) {
        throw new WebhookRequestException(httpResponse, text, bodyWait(bodyRetryAfter, text));
      }
      return text;
    };

  /// The body reader of [#post(String)]: no format, so no wait is read from the body.
  private static final Function<String, Duration> NO_BODY_WAIT = _ -> null;

  /// What `reader` reads from `text`, or null when it throws a RuntimeException, so a faulty
  /// custom format cannot hide the real failure.
  private static Duration bodyWait(final Function<String, Duration> reader, final String text) {
    try {
      return reader.apply(text);
    } catch (final RuntimeException faultyFormat) {
      return null;
    }
  }

  WebhookClientImpl(final URI endpoint,
                    final HttpClient httpClient,
                    final Duration requestTimeout,
                    final UnaryOperator<HttpRequest.Builder> extendRequest,
                    final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
    super(endpoint, httpClient, requestTimeout, extendRequest, testResponse);
  }

  @Override
  public CompletableFuture<String> post(final String jsonBody) {
    return send(jsonBody, NO_BODY_WAIT);
  }

  @Override
  public CompletableFuture<String> post(final String jsonBody, final WebhookFormat format) {
    Objects.requireNonNull(format, "'format' is required.");
    return send(jsonBody, format::retryAfter);
  }

  private CompletableFuture<String> send(final String jsonBody,
                                         final Function<String, Duration> bodyRetryAfter) {
    return sendPostRequest(httpResponse -> RESPONSE_PARSER.apply(httpResponse, bodyRetryAfter), requestTimeout, jsonBody);
  }

  @Override
  public String toString() {
    // host only: webhook URL paths (e.g. Slack) carry the credential
    return "WebhookClientImpl{host=" + endpoint.getHost() + '}';
  }
}
