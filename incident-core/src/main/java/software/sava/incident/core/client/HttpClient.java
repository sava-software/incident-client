package software.sava.incident.core.client;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

public interface HttpClient {

  URI endpoint();

  java.net.http.HttpClient httpClient();

  abstract class Builder<B extends Builder<B>> {

    protected URI endpoint;
    protected java.net.http.HttpClient httpClient;
    protected Duration requestTimeout;
    protected UnaryOperator<HttpRequest.Builder> extendRequest;
    protected BiPredicate<HttpResponse<?>, byte[]> testResponse;

    protected abstract B builder();
    
    public B endpoint(final URI endpoint) {
      this.endpoint = endpoint;
      return builder();
    }

    public B httpClient(final java.net.http.HttpClient httpClient) {
      this.httpClient = httpClient;
      return builder();
    }

    public B requestTimeout(final Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
      return builder();
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
