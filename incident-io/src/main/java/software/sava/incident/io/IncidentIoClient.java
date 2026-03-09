package software.sava.incident.io;

import software.sava.incident.core.client.HttpApiClient;

import java.util.concurrent.CompletableFuture;

public interface IncidentIoClient extends HttpApiClient {

  static Builder clientBuilder() {
    return new Builder();
  }

  CompletableFuture<CreateIncidentResponse> createIncident(final CreateIncidentRequest request);

  final class Builder extends HttpApiClient.Builder<Builder> {

    private Builder() {
    }

    public IncidentIoClient createClient() {
      setDefaults();
      if (endpoint == null) {
        endpoint("https://api.incident.io/v2/incidents");
      }
      return new IncidentIoClientImpl(
          endpoint,
          httpClient,
          requestTimeout,
          extendRequest,
          testResponse
      );
    }

    public Builder bearerToken(final String bearerToken) {
      return extendRequest(builder -> {
        builder.setHeader("Authorization", "Bearer " + bearerToken);
        builder.setHeader("Content-Type", "application/json");
        return builder;
      });
    }
  }
}
