package software.sava.incident.pagerduty.event.client;

import software.sava.incident.core.api.IncidentClient;
import software.sava.incident.core.client.HttpApiClient;
import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;

import java.util.concurrent.CompletableFuture;

public interface PagerDutyEventClient extends HttpApiClient {

  static Builder clientBuilder() {
    return new Builder();
  }

  /// Adapts this client to the provider-neutral [IncidentClient]; see
  /// [PagerDutyIncidentClient] for how alerts map to trigger events.
  default IncidentClient asIncidentClient() {
    return PagerDutyIncidentClient.createClient(this);
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

  /// Triggers `payload` on the default routing key; see
  /// [#triggerEvent(String, String, String, PagerDutyEventPayload)] for how a builder is handled.
  default CompletableFuture<PagerDutyEventResponse> triggerDefaultRouteEvent(final PagerDutyEventPayload payload) {
    return triggerEvent(defaultRoutingKey(), payload);
  }

  /// Triggers `payload` with the default client name and URL; see
  /// [#triggerEvent(String, String, String, PagerDutyEventPayload)] for how a builder is handled.
  default CompletableFuture<PagerDutyEventResponse> triggerEvent(final String routingKey,
                                                                 final PagerDutyEventPayload payload) {
    return triggerEvent(defaultClientName(), defaultClientUrl(), routingKey, payload);
  }

  /// Sends a trigger event.
  ///
  /// A [PagerDutyEventPayload.Builder] handed here is `create()`d, and the created payload
  /// is what is sent, so an incomplete builder is refused as `create()` refuses it rather
  /// than serialized with missing required fields. `create()` is called on the builder
  /// itself, never on a copy: the builder keeps the dedup key it generated and the
  /// timestamp it defaulted, so every later hand-off of the same builder, including each
  /// attempt of a [software.sava.incident.pagerduty.event.service.PagerDutyService] retry,
  /// sends the same `dedup_key` and timestamp. Do not reuse a builder for a distinct event.
  ///
  /// @throws NullPointerException     synchronously, before anything is sent, if
  ///                                  `routingKey` or `payload` is null
  /// @throws RuntimeException         synchronously, before anything is sent, whatever the
  ///                                  builder's `create()` throws
  CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                         final String clientUrl,
                                                         final String routingKey,
                                                         final PagerDutyEventPayload payload);

  /// Sends `payload` as a change event on the default routing key; see
  /// [#changeEvent(String, PagerDutyChangeEventPayload)] for how a builder is handled.
  default CompletableFuture<PagerDutyEventResponse> defaultRouteChangeEvent(final PagerDutyChangeEventPayload payload) {
    return changeEvent(defaultRoutingKey(), payload);
  }

  /// Sends a change event.
  ///
  /// A [PagerDutyChangeEventPayload.Builder] handed here is `create()`d on the builder
  /// itself, never on a copy, and the created payload is what is sent, as
  /// [#triggerEvent(String, String, String, PagerDutyEventPayload)] does: the builder keeps
  /// its defaulted timestamp, every later hand-off of it sends the same event, and it must
  /// not be reused for a distinct event. A [PagerDutyEventPayload.Builder] is a change
  /// builder too and is created by its own rules, so it also requires `source` and
  /// `severity`, and the event payload it creates is what is sent, exactly as if it had been
  /// created and passed here. For a change event derived from an event, pass
  /// `PagerDutyChangeEventPayload.build(payload).create()`.
  ///
  /// @throws NullPointerException synchronously, before anything is sent, if `routingKey`
  ///                              or `payload` is null
  /// @throws RuntimeException     synchronously, before anything is sent, whatever the
  ///                              builder's `create()` throws
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
