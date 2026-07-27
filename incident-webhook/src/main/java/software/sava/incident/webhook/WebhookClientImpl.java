package software.sava.incident.webhook;

import software.sava.incident.webhook.exceptions.WebhookRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.nio.charset.StandardCharsets.UTF_8;

final class WebhookClientImpl extends JsonHttpClient implements WebhookClient {

  private static final Function<HttpResponse<?>, String> RESPONSE_PARSER = httpResponse -> {
    final byte[] body = readBody(httpResponse);
    final int statusCode = httpResponse.statusCode();
    if (statusCode < 200 || statusCode >= 300) {
      throw new WebhookRequestException(httpResponse, body);
    }
    // new String(byte[0]) is already ""; only the defensive null needs a branch
    return body == null ? "" : new String(body, UTF_8);
  };

  WebhookClientImpl(final URI endpoint,
                    final HttpClient httpClient,
                    final Duration requestTimeout,
                    final UnaryOperator<HttpRequest.Builder> extendRequest,
                    final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
    super(endpoint, httpClient, requestTimeout, extendRequest, testResponse);
  }

  @Override
  public CompletableFuture<String> post(final String jsonBody) {
    return sendPostRequest(RESPONSE_PARSER, requestTimeout, jsonBody);
  }

  @Override
  public String toString() {
    // host only: webhook URL paths (e.g. Slack) carry the credential
    return "WebhookClientImpl{host=" + endpoint.getHost() + '}';
  }
}
