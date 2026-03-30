package software.sava.incident.core.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

public interface HttpApiClient {

  URI endpoint();

  HttpClient httpClient();

  abstract class Builder<B extends Builder<B>> {

    protected URI endpoint;
    protected java.net.http.HttpClient httpClient;
    protected Duration requestTimeout;
    protected UnaryOperator<HttpRequest.Builder> extendRequest;
    protected BiPredicate<HttpResponse<?>, byte[]> testResponse;

    protected final B builder() {
      //noinspection unchecked
      return (B) this;
    }

    protected void setDefaults() {
      if (this.httpClient == null) {
        this.httpClient = HttpClient.newHttpClient();
      }
      if (this.requestTimeout == null) {
        this.requestTimeout = Duration.ofSeconds(8);
      }
    }

    public B endpoint(final URI endpoint) {
      this.endpoint = endpoint;
      return builder();
    }

    public B endpoint(final String endpoint) {
      return endpoint(URI.create(endpoint));
    }

    public B httpClient(final HttpClient httpClient) {
      this.httpClient = httpClient;
      return builder();
    }

    public B requestTimeout(final Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return builder();
    }

    public URI endpoint() {
      return endpoint;
    }

    public HttpClient httpClient() {
      return httpClient;
    }

    public Duration requestTimeout() {
      return requestTimeout;
    }

    public UnaryOperator<HttpRequest.Builder> extendRequest() {
      return extendRequest;
    }

    public BiPredicate<HttpResponse<?>, byte[]> testResponse() {
      return testResponse;
    }

    public B extendRequest(final UnaryOperator<HttpRequest.Builder> extendRequest) {
      this.extendRequest = extendRequest;
      return builder();
    }

    public B testResponse(final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
      this.testResponse = testResponse;
      return builder();
    }
  }
}
