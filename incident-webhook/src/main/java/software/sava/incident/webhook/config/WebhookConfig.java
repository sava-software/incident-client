package software.sava.incident.webhook.config;

import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.webhook.WebhookClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Configuration for a [WebhookClient]. `endpoint` is required — it is the user's own
/// webhook URL and there is no meaningful default. `requestTimeout`, static `headers`,
/// and a `bearerToken` convenience (an `Authorization: Bearer ...` header) are optional.
///
/// Properties headers are keyed under `headers.`, JSON headers as an object:
///
/// ```
/// incident.endpoint=https://hooks.example.com/notify
/// incident.headers.X-Api-Key=KEY
/// ```
///
/// ```json
/// {"endpoint": "https://hooks.example.com/notify", "headers": {"X-Api-Key": "KEY"}}
/// ```
public final class WebhookConfig extends HttpApiClientConfig {

  private final String bearerToken;
  private final Map<String, String> headers;

  private WebhookConfig(final URI endpoint,
                        final Duration requestTimeout,
                        final String bearerToken,
                        final Map<String, String> headers) {
    super(endpoint, requestTimeout);
    this.bearerToken = bearerToken;
    this.headers = headers;
  }

  public WebhookClient.Builder createClientBuilder() {
    return createClientBuilder(WebhookClient.clientBuilder());
  }

  public WebhookClient.Builder createClientBuilder(final WebhookClient.Builder builder) {
    // endpoint is validated present at parse time
    builder.endpoint(endpoint);
    if (requestTimeout != null) {
      builder.requestTimeout(requestTimeout);
    }
    headers.forEach(builder::header);
    if (bearerToken != null && !bearerToken.isBlank()) {
      builder.bearerToken(bearerToken);
    }
    return builder;
  }

  public static WebhookConfig parseConfig(final Properties properties) {
    return parseConfig(properties, null);
  }

  public static WebhookConfig parseConfig(final Properties properties, final String prefix) {
    final var parser = new Parser(prefix);
    parser.parseConfig(properties);
    return parser.createConfig();
  }

  public static WebhookConfig parseConfig(final JsonIterator ji) {
    final var parser = new Parser(null);
    ji.testObject(parser);
    return parser.createConfig();
  }

  public String bearerToken() {
    return bearerToken;
  }

  /// Static request headers: properties sorted by header name, JSON in document order.
  public Map<String, String> headers() {
    return headers;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    private String bearerToken;
    private final Map<String, String> headers;

    private Parser(final String prefix) {
      super(prefix);
      this.headers = new LinkedHashMap<>();
    }

    private WebhookConfig createConfig() {
      if (endpoint == null) {
        throw new IllegalStateException("WebhookConfig endpoint is required.");
      }
      return new WebhookConfig(
          endpoint,
          requestTimeout,
          bearerToken,
          // header order is contractual (JSON document order) and Map.copyOf would
          // shuffle it, so every size takes the order-preserving copy
          Collections.unmodifiableMap(new LinkedHashMap<>(headers))
      );
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.bearerToken = properties.getProperty(prefix + "bearerToken");
      final var headerPrefix = prefix + "headers.";
      // sorted for a deterministic header order; Properties iteration order is not stable
      for (final var name : new TreeSet<>(properties.stringPropertyNames())) {
        if (name.startsWith(headerPrefix)) {
          final var header = name.substring(headerPrefix.length());
          if (!header.isBlank()) {
            headers.put(header, properties.getProperty(name));
          }
        }
      }
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("bearerToken", buf, offset, len)) {
        this.bearerToken = ji.readString();
      } else if (fieldEquals("headers", buf, offset, len)) {
        ji.readMap(headers, String::new, (header, headerJi) -> headerJi.readString());
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
