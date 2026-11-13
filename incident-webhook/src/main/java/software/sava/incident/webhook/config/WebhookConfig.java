package software.sava.incident.webhook.config;

import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.webhook.WebhookClient;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

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
/// unused otherwise), and `messageThreadId` the forum topic within it (optional, read by
/// `telegram` alone; absent, a blank property, a JSON null, or zero means no topic).
/// `messageThreadId` is an integer from 0 to [Integer#MAX_VALUE]: ASCII digits alone in
/// properties, a JSON number, never a string, in JSON; anything else fails the parse with
/// an [IllegalStateException] naming the field and the value, or for a JSON token that is
/// neither a number nor a string its type, whichever provider is configured.
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
  private final int messageThreadId;

  private WebhookConfig(final URI endpoint,
                        final Duration requestTimeout,
                        final String bearerToken,
                        final Map<String, String> headers,
                        final String chatId,
                        final int messageThreadId) {
    super(endpoint, requestTimeout);
    this.bearerToken = bearerToken;
    this.headers = headers;
    this.chatId = chatId;
    this.messageThreadId = messageThreadId;
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
    return ji.parseObject(parser(), Parser::createConfig);
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

  /// Forum topic within [#chatId()] for chat-targeted providers, Telegram's
  /// `message_thread_id`; 0, meaning no topic, when not configured.
  public int messageThreadId() {
    return messageThreadId;
  }

  /// `value`, a `messageThreadId` as configured: 0 when null or blank; otherwise ASCII
  /// digits alone that fit an `int`, else an [IllegalStateException] naming the field and
  /// the value. The digit check comes first because [Integer#parseInt(String)] also takes a
  /// sign and non-ASCII digits.
  private static int parseMessageThreadId(final String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    for (int i = 0; i < value.length(); ++i) {
      final char c = value.charAt(i);
      if (c < '0' || c > '9') {
        throw invalidMessageThreadId(value);
      }
    }
    try {
      return Integer.parseInt(value);
    } catch (final NumberFormatException overIntMaxValue) {
      throw invalidMessageThreadId(value);
    }
  }

  private static IllegalStateException invalidMessageThreadId(final String value) {
    return new IllegalStateException(
        "WebhookConfig messageThreadId must be an integer from 0 to " + Integer.MAX_VALUE + ": '" + value + "'."
    );
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
    private int messageThreadId;

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
          chatId,
          messageThreadId
      );
    }

    /// Reads this config's properties under the parser's prefix; a composing factory
    /// reads its own additional properties alongside.
    @Override
    public void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.bearerToken = properties.getProperty(prefix + "bearerToken");
      this.chatId = properties.getProperty(prefix + "chatId");
      this.messageThreadId = parseMessageThreadId(properties.getProperty(prefix + "messageThreadId"));
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
      } else if (fieldEquals("messageThreadId", buf, offset, len)) {
        this.messageThreadId = readMessageThreadId(ji);
      } else if (fieldEquals("headers", buf, offset, len)) {
        ji.readMap(headers, String::new, (header, headerJi) -> headerJi.readString());
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }

    /// A JSON number token as [#parseMessageThreadId(String)] reads it, `null` as absent;
    /// any other token, a numeric string included, is refused.
    private static int readMessageThreadId(final JsonIterator ji) {
      final var next = ji.whatIsNext();
      if (next == ValueType.NUMBER) {
        return parseMessageThreadId(ji.readNumberAsString());
      } else if (next == ValueType.NULL) {
        ji.skip();
        return 0;
      } else if (next == ValueType.STRING) {
        throw new IllegalStateException(
            "WebhookConfig messageThreadId must be a JSON number, not the string '" + ji.readString() + "'."
        );
      } else {
        throw new IllegalStateException("WebhookConfig messageThreadId must be a JSON number, not " + next + '.');
      }
    }
  }
}
