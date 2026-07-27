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
/// `chatId` is the destination for chat-targeted providers (required by `telegram`,
/// unused otherwise).
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
  private final String chatId;

  private WebhookConfig(final URI endpoint,
                        final Duration requestTimeout,
                        final String bearerToken,
                        final Map<String, String> headers,
                        final String chatId) {
    super(endpoint, requestTimeout);
    this.bearerToken = bearerToken;
    this.headers = headers;
    this.chatId = chatId;
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
    final var parser = parser(prefix);
    parser.parseConfig(properties);
    return parser.createConfig();
  }

  public static WebhookConfig parseConfig(final JsonIterator ji) {
    final var parser = parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  public static Parser parser() {
    return new Parser(null);
  }

  public static Parser parser(final String prefix) {
    return new Parser(prefix);
  }

  public String bearerToken() {
    return bearerToken;
  }

  /// Static request headers: properties sorted by header name, JSON in document order.
  public Map<String, String> headers() {
    return headers;
  }

  /// Destination chat for chat-targeted providers, e.g. a Telegram numeric chat id or
  /// `@channelusername`; null when not configured.
  public String chatId() {
    return chatId;
  }

  /// Public for composition by third-party provider factories whose config carries
  /// fields beyond this one's: parse your own fields first and delegate everything else
  /// here, so `endpoint`/`requestTimeout`/`headers`/`bearerToken` handling — and the
  /// strict unknown-field error — stay shared:
  ///
  /// ```java
  /// final var parser = WebhookConfig.parser();
  /// ji.testObject((buf, offset, len, ji2) -> {
  ///   if (fieldEquals("roomId", buf, offset, len)) {
  ///     this.roomId = ji2.readString();
  ///     return true;
  ///   }
  ///   return parser.test(buf, offset, len, ji2);
  /// });
  /// final var config = parser.createConfig();
  /// ```
  public static final class Parser extends HttpApiClientConfig.Parser {

    private String bearerToken;
    private final Map<String, String> headers;
    private String chatId;

    private Parser(final String prefix) {
      super(prefix);
      this.headers = new LinkedHashMap<>();
    }

    /// The normalized key prefix (dot-terminated, or empty) — composing factories read
    /// their own properties as `prefix() + "field"`.
    public String prefix() {
      return prefix;
    }

    /// Validates and creates the parsed config; `endpoint` is required.
    public WebhookConfig createConfig() {
      if (endpoint == null) {
        throw new IllegalStateException("WebhookConfig endpoint is required.");
      }
      return new WebhookConfig(
          endpoint,
          requestTimeout,
          bearerToken,
          // header order is contractual (JSON document order) and Map.copyOf would
          // shuffle it, so every size takes the order-preserving copy
          Collections.unmodifiableMap(new LinkedHashMap<>(headers)),
          chatId
      );
    }

    /// Reads this config's properties under the parser's prefix; a composing factory
    /// reads its own additional properties alongside.
    @Override
    public void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.bearerToken = properties.getProperty(prefix + "bearerToken");
      this.chatId = properties.getProperty(prefix + "chatId");
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
      } else if (fieldEquals("chatId", buf, offset, len)) {
        this.chatId = ji.readString();
      } else if (fieldEquals("headers", buf, offset, len)) {
        ji.readMap(headers, String::new, (header, headerJi) -> headerJi.readString());
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
