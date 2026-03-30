package software.sava.incident.pagerduty.event.client;

import software.sava.incident.core.client.HttpApiClient;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;

import java.util.concurrent.CompletableFuture;

public interface PagerDutyEventClient extends HttpApiClient {

  static Builder clientBuilder() {
    return new Builder();
  }

  String defaultClientName();

  String defaultClientUrl();

  String defaultRoutingKey();

  default CompletableFuture<PagerDutyEventResponse> acknowledgeEvent(final String dedupKey) {
    return acknowledgeEvent(defaultRoutingKey(), dedupKey);
  }

  CompletableFuture<PagerDutyEventResponse> acknowledgeEvent(final String routingKey, final String dedupKey);

  default CompletableFuture<PagerDutyEventResponse> resolveEvent(final String dedupKey) {
    return resolveEvent(defaultRoutingKey(), dedupKey);
  }

  CompletableFuture<PagerDutyEventResponse> resolveEvent(final String routingKey, final String dedupKey);

  default CompletableFuture<PagerDutyEventResponse> triggerDefaultRouteEvent(final PagerDutyEventPayload payload) {
    return triggerEvent(defaultRoutingKey(), payload);
  }

  default CompletableFuture<PagerDutyEventResponse> triggerEvent(final String routingKey,
                                                                 final PagerDutyEventPayload payload) {
    return triggerEvent(defaultClientName(), defaultClientUrl(), routingKey, payload);
  }

  CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                         final String clientUrl,
                                                         final String routingKey,
                                                         final PagerDutyEventPayload payload);

  default CompletableFuture<PagerDutyEventResponse> defaultRouteChangeEvent(final PagerDutyChangeEventPayload payload) {
    return changeEvent(defaultRoutingKey(), payload);
  }

  CompletableFuture<PagerDutyEventResponse> changeEvent(final String routingKey,
                                                        final PagerDutyChangeEventPayload payload);

  final class Builder extends HttpApiClient.Builder<Builder> {

    private String defaultClientName;
    private String defaultClientUrl;
    private String defaultRoutingKey;
    private String authToken;

    private Builder() {
    }

    public PagerDutyEventClient createClient() {
      setDefaults();
      if (endpoint == null) {
        endpoint("https://events.pagerduty.com");
      }
      if (this.extendRequest == null) {
        if (authToken == null || authToken.isBlank()) {
          extendRequest(builder -> builder.setHeader("Content-Type", "application/json"));
        } else {
          final var authHeader = "Token token=" + authToken;
          extendRequest(builder -> {
            builder.setHeader("Authorization", authHeader);
            builder.setHeader("Content-Type", "application/json");
            return builder;
          });
        }

      }
      return new PagerDutyEventClientImpl(
          defaultClientName,
          defaultClientUrl,
          defaultRoutingKey,
          endpoint,
          httpClient,
          requestTimeout,
          extendRequest,
          testResponse
      );
    }

    public Builder defaultClientName(final String defaultClientName) {
      this.defaultClientName = defaultClientName;
      return this;
    }

    public Builder defaultClientUrl(final String defaultClientUrl) {
      this.defaultClientUrl = defaultClientUrl;
      return this;
    }

    public Builder defaultRoutingKey(final String defaultRoutingKey) {
      this.defaultRoutingKey = defaultRoutingKey;
      return this;
    }

    public Builder authToken(final String authToken) {
      this.authToken = authToken;
      return this;
    }

    public String defaultClientName() {
      return defaultClientName;
    }

    public String defaultClientUrl() {
      return defaultClientUrl;
    }

    public String defaultRoutingKey() {
      return defaultRoutingKey;
    }

    public String authToken() {
      return authToken;
    }
  }
}
