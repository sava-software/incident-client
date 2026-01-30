package software.sava.incident.io;

import java.net.http.HttpClient;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public interface IncidentIoClient extends software.sava.incident.core.client.HttpClient {

  static Builder buildClient() {
    return new Builder();
  }

  CompletableFuture<CreateIncidentResponse> createIncident(final CreateIncidentRequest request);

  final class Builder extends software.sava.incident.core.client.HttpClient.Builder<Builder> {

    private Builder() {
    }

    public IncidentIoClient createClient() {
      return new IncidentIoClientImpl(
          Objects.requireNonNull(endpoint),
          httpClient == null ? HttpClient.newHttpClient() : httpClient,
          requestTimeout,
          extendRequest,
          testResponse
      );
    }

    @Override
    protected Builder builder() {
      return this;
    }
  }
}
