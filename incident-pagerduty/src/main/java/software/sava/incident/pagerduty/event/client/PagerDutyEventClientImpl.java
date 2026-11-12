package software.sava.incident.pagerduty.event.client;

import software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyEventResponse;
import software.sava.incident.pagerduty.exceptions.PagerDutyParseException;
import software.sava.incident.pagerduty.exceptions.PagerDutyRequestException;
import software.sava.rpc.json.http.client.JsonHttpClient;
import systems.comodal.jsoniter.ContextFieldIndexPredicate;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static software.sava.incident.core.json.JsonUtil.escapeJson;

final class PagerDutyEventClientImpl extends JsonHttpClient implements PagerDutyEventClient {

  private static final FieldMatcher EXCEPTION_FIELDS = FieldMatcher.of("status", "message", "errors");

  private static final ContextFieldIndexPredicate<PagerDutyRequestException.Builder> EXCEPTION_PARSER = (exception, fieldIndex, ji) -> {
    switch (fieldIndex) {
      case 0 -> exception.status(ji.readString());
      case 1 -> exception.message(ji.readString());
      case 2 -> {
        while (ji.readArray()) {
          exception.error(ji.readString());
        }
      }
      default -> ji.skip();
    }
    return true;
  };

  // package-private for direct tests: the status mapping and envelope parsing are
  // in-process logic; only the transport methods need a wire to exercise
  static RuntimeException errorResponse(final HttpResponse<?> response) {
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
      responseError = ji.testObject(exception, EXCEPTION_FIELDS, EXCEPTION_PARSER).create();
    } catch (final RuntimeException runtimeCause) {
      throw new PagerDutyParseException(response,
          String.format("Failed to adapt %d error response: '%s'", statusCode, new String(body)),
          runtimeCause
      );
    }
    throw responseError;
  }

  private static final Function<HttpResponse<?>, PagerDutyEventResponse> RESPONSE_PARSER = httpResponse -> {
    final var code = httpResponse.statusCode();
    if (code < 200 || code >= 300) {
      throw errorResponse(httpResponse);
    } else {
      // a 2xx means the event was accepted, so a body this parser cannot read (a bad gzip
      // encoding: reported with empty text) or cannot parse must not surface as a transport
      // failure, which the retry wrappers would send again; a caller-set testResponse makes
      // the transport read the body before this parser runs, outside this guard
      byte[] body = null;
      try {
        body = readBody(httpResponse);
        return PagerDutyEventResponse.parser().parse(JsonIterator.parse(body)).create();
      } catch (final RuntimeException parseCause) {
        throw new PagerDutyParseException(httpResponse,
            String.format("Failed to adapt %d response: '%s'", code, body == null ? "" : new String(body)),
            parseCause
        );
      }
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
    super(baseUri, httpClient, requestTimeout, extendRequest, testResponse);
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
    // measured in the encoding the transport sends: String.length() undercounts multi-byte text
    final int bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8).length;
    if (bodyBytes > MAX_REQUEST_BODY_BYTES) {
      throw new IllegalArgumentException(String.format(
          "PagerDuty Events API request body is %d bytes, over the %d-byte limit.",
          bodyBytes, MAX_REQUEST_BODY_BYTES
      ));
    }
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
        escapeJson(routingKey), escapeJson(dedupKey), eventAction
    );
    return postEvent(eventUri, json);
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> triggerEvent(final String clientName,
                                                                final String clientUrl,
                                                                final String routingKey,
                                                                final PagerDutyEventPayload payload) {
    Objects.requireNonNull(routingKey, "Routing key is a required field.");
    Objects.requireNonNull(payload, "Payload is a required field.");
    // a builder is created in place, never copied: it keeps the generated dedup key and
    // defaulted timestamp, so every hand-off of it (each service retry) sends the same event
    final var event = payload instanceof PagerDutyEventPayload.Builder builder ? builder.create() : payload;
    final var payloadJson = event.payloadJson();
    final var linksJson = event.linksJson();
    final var imagesJson = event.imagesJson();
    final var json = String.format("""
            {"event_action":"trigger","routing_key":"%s","dedup_key":"%s","payload":%s%s%s%s%s}""",
        escapeJson(routingKey), escapeJson(event.dedupKey()), payloadJson,
        (clientName == null || clientName.isBlank() ? "" : String.format("""
            ,"client":"%s\"""", escapeJson(clientName))),
        (clientUrl == null || clientUrl.isBlank() ? "" : String.format("""
            ,"client_url":"%s\"""", escapeJson(clientUrl))),
        imagesJson, linksJson
    );
    return postEvent(eventUri, json);
  }

  @Override
  public CompletableFuture<PagerDutyEventResponse> changeEvent(final String routingKey,
                                                               final PagerDutyChangeEventPayload payload) {
    Objects.requireNonNull(routingKey, "Routing key is a required field.");
    Objects.requireNonNull(payload, "Payload is a required field.");
    // an event builder is a change builder too, and is created by its own rules
    final var change = payload instanceof PagerDutyChangeEventPayload.Builder builder ? builder.create() : payload;
    final var payloadJson = change.payloadJson();
    final var linksJson = change.linksJson();
    final var imagesJson = change.imagesJson();
    final var json = String.format("""
            {"routing_key":"%s","payload":%s%s%s}""",
        escapeJson(routingKey), payloadJson, linksJson, imagesJson
    );
    return postEvent(changeEventUri, json);
  }

  @Override
  public String toString() {
    return "PagerDutyEventClientImpl{defaultClientName='" + defaultClientName + "'}";
  }
}
