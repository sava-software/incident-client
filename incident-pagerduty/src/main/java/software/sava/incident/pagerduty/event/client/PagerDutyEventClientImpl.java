package software.sava.incident.pagerduty.event.client;

import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;
import software.sava.incident.pagerduty.exceptions.PagerDutyParseException;
import software.sava.incident.pagerduty.exceptions.PagerDutyRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;
import systems.comodal.jsoniter.ContextFieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class PagerDutyEventClientImpl extends JsonHttpClient implements PagerDutyEventClient {

  private static final ContextFieldBufferPredicate<PagerDutyRequestException.Builder> EXCEPTION_PARSER = (exception, buf, offset, len, ji) -> {
    if (fieldEquals("status", buf, offset, len)) {
      exception.status(ji.readString());
    } else if (fieldEquals("message", buf, offset, len)) {
      exception.message(ji.readString());
    } else if (fieldEquals("code", buf, offset, len)) {
      exception.message(ji.readString());
    } else if (fieldEquals("errors", buf, offset, len)) {
      while (ji.readArray()) {
        exception.error(ji.readString());
      }
    } else {
      ji.skip();
    }
    return true;
  };

  private static RuntimeException errorResponse(final HttpResponse<?> response) {
    final var exception = PagerDutyRequestException.build(response);
    final int statusCode = response.statusCode();
    if (statusCode == 429) {
      throw exception.message("Too many requests").create();
    } else if (statusCode == 404) {
      throw exception.message(response.request().uri() + " Not Found").create();
    } else if (statusCode == 400) {
      exception.message("Bad Request - Check that the JSON is valid.");
    } else if (statusCode == 401) {
      exception.message("Unauthorized");
    } else if (statusCode == 402) {
      exception.message("Payment Required");
    } else if (statusCode == 403) {
      exception.message("Forbidden");
    } else if (statusCode >= 500 && statusCode < 600) {
      exception.message("Internal Server Error - the PagerDuty server experienced an error while processing the event.");
    }
    final byte[] body = readBody(response);
    final var ji = JsonIterator.parse(body);
    final RuntimeException responseError;
    try {
      responseError = ji.testObject(exception, EXCEPTION_PARSER).create();
    } catch (final RuntimeException runtimeCause) {
      try {
        throw new PagerDutyParseException(response,
            String.format("Failed to adapt %d error response: '%s'", statusCode, ji.currentBuffer()),
            runtimeCause
        );
      } catch (final RuntimeException ex) {
        throw new PagerDutyParseException(response,
            String.format("Failed to adapt %d error response: '%s'", statusCode, new String(body)),
            runtimeCause
        );
      }
    }
    throw responseError;
  }

  private static final Function<HttpResponse<?>, PagerDutyEventResponse> RESPONSE_PARSER = httpResponse -> {
    final var code = httpResponse.statusCode();
    if (code < 200 || code >= 300) {
      throw errorResponse(httpResponse);
    } else {
      final var ji = JsonIterator.parse(readBody(httpResponse));
      final var response = PagerDutyEventResponse.parser();
      ji.testObject(response);
      return response.create();
    }
  };

  private final String defaultClientName;
  private final String defaultClientUrl;
  private final String defaultRoutingKey;
  private final URI eventUri;
  private final URI changeEventUri;

  PagerDutyEventClientImpl(final String defaultClientName,
                           final String defaultClientUrl,
                           final String defaultRoutingKey,
                           final URI baseUri,
                           final HttpClient httpClient,
                           final Duration requestTimeout,
                           final UnaryOperator<HttpRequest.Builder> extendRequest,
                           final BiPredicate<HttpResponse<?>, byte[]> testResponse) {
    super(baseUri, httpClient, requestTimeout, extendRequest, null, testResponse);
    this.defaultClientName = defaultClientName;
    this.defaultClientUrl = defaultClientUrl;
    this.defaultRoutingKey = defaultRoutingKey;
    this.eventUri = baseUri.resolve("/v2/enqueue");
    this.changeEventUri = baseUri.resolve("/v2/change/enqueue");
  }

  @Override
  public String defaultClientName() {
    return defaultClientName;
  }

  @Override
  public String defaultClientUrl() {
    return defaultClientUrl;
  }

  @Override
  public String defaultRoutingKey() {
    return defaultRoutingKey;
  }

  private CompletableFuture<PagerDutyEventResponse> postEvent(final URI uri, final String jsonBody) {
    return sendPostRequest(uri, RESPONSE_PARSER, jsonBody);
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> acknowledgeEvent(final String routingKey, final String dedupKey) {
    return eventAction(routingKey, dedupKey, "acknowledge");
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> resolveEvent(final String routingKey, final String dedupKey) {
    return eventAction(routingKey, dedupKey, "resolve");
  }

  private CompletableFuture<PagerDutyEventResponse> eventAction(final String routingKey,
                                                                final String dedupKey,
                                                                final String eventAction) {
    Objects.requireNonNull(routingKey, "Routing key is a required field.");
    Objects.requireNonNull(dedupKey, "De-duplication key is a required field.");
    final var json = String.format("""
            {"routing_key":"%s","dedup_key":"%s","event_action":"%s"}""",
        routingKey, dedupKey, eventAction
    );
    return postEvent(eventUri, json);
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                                final String clientUrl,
                                                                final String routingKey,
                                                                final PagerDutyEventPayload payload) {
    Objects.requireNonNull(routingKey, "Routing key is a required field.");
    final var payloadJson = payload.payloadJson();
    final var linksJson = payload.linksJson();
    final var imagesJson = payload.imagesJson();
    final var json = String.format("""
            {"event_action":"trigger","routing_key":"%s","dedup_key":"%s","payload":%s,"client":"%s"%s%s%s}""",
        routingKey, payload.dedupKey(), payloadJson, clientName,
        (clientUrl == null ? "" : ",\"client_url\":\"" + clientUrl + '"'),
        imagesJson, linksJson
    );
    return postEvent(eventUri, json);
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> changeEvent(final String routingKey,
                                                               final PagerDutyChangeEventPayload payload) {
    Objects.requireNonNull(routingKey, "Routing key is a required field.");
    final var payloadJson = payload.payloadJson();
    final var linksJson = payload.linksJson();
    final var imagesJson = payload.imagesJson();
    final var json = String.format("""
            {"routing_key":"%s","payload":%s%s%s}""",
        routingKey, payloadJson, linksJson, imagesJson
    );
    return postEvent(changeEventUri, json);
  }

  @Override
  public String toString() {
    return "PagerDutyHttpEventClient{defaultClientName='" + defaultClientName + '}';
  }
}
