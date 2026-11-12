package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.client.HttpApiClient;

import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/// POSTs JSON documents to a single configured webhook endpoint. The endpoint is the
/// user's own webhook URL — a Slack incoming webhook, a custom receiver, an automation
/// platform — so unlike the provider clients there is no default; `endpoint` is required.
public interface WebhookClient extends HttpApiClient {

  static Builder clientBuilder() {
    return new Builder();
  }

  /// POSTs `jsonBody` to the configured endpoint with `Content-Type: application/json`.
  /// Completes with the raw UTF-8 response body on a 2xx status, and fails with a
  /// `WebhookRequestException` on any other, whose wait is only the `Retry-After` header's:
  /// no body is read for one; see [#post(String, WebhookFormat)].
  CompletableFuture<String> post(final String jsonBody);

  /// As [#post(String)], and a non-2xx failure also carries the wait `format` reads from the
  /// response body ([WebhookFormat#retryAfter(String)]); a `Retry-After` header that parses
  /// still wins. Throws a NullPointerException at once for a null `format`.
  ///
  /// The default ignores `format` beyond that null check and returns [#post(String)]'s
  /// future, so its failures state only the header's wait: an implementation that builds
  /// its own failures overrides this to hand `format` the body.
  default CompletableFuture<String> post(final String jsonBody, final WebhookFormat format) {
    Objects.requireNonNull(format, "'format' is required.");
    return post(jsonBody);
  }

  /// Adapts this client to the provider-neutral [IncidentClient], rendering alerts with
  /// `format`; see [WebhookIncidentClient].
  default IncidentClient incidentClient(final WebhookFormat format) {
    return WebhookIncidentClient.createClient(this, format);
  }

  final class Builder extends HttpApiClient.Builder<Builder> {

    private final Map<String, String> headers;

    private Builder() {
      this.headers = new LinkedHashMap<>();
    }

    public WebhookClient createClient() {
      Objects.requireNonNull(endpoint, "'endpoint' is required.");
      setDefaults();
      var extendRequest = this.extendRequest;
      if (!headers.isEmpty()) {
        // copy so later builder mutations cannot reach a created client
        final var staticHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        final UnaryOperator<HttpRequest.Builder> applyHeaders = requestBuilder -> {
          staticHeaders.forEach(requestBuilder::setHeader);
          return requestBuilder;
        };
        final var extend = extendRequest;
        extendRequest = extend == null
            ? applyHeaders
            : requestBuilder -> extend.apply(applyHeaders.apply(requestBuilder));
      }
      return new WebhookClientImpl(
          endpoint,
          httpClient,
          requestTimeout,
          extendRequest,
          testResponse
      );
    }

    /// Sets a static header on every request, for receivers that authenticate by header
    /// rather than by URL. Setting the same name again replaces the value.
    public Builder header(final String name, final String value) {
      headers.put(name, value);
      return this;
    }

    public Builder bearerToken(final String bearerToken) {
      return header("Authorization", "Bearer " + bearerToken);
    }
  }
}
