package software.sava.incident.pagerduty.config;

import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public final class PagerDutyConfig extends HttpApiClientConfig {

  private final String routingKey;
  private final String authToken;

  private PagerDutyConfig(final URI endpoint,
                          final Duration requestTimeout,
                          final String routingKey,
                          final String authToken) {
    super(endpoint, requestTimeout);
    this.routingKey = routingKey;
    this.authToken = authToken;
  }

  public PagerDutyEventClient.Builder createClientBuilder() {
    return createClientBuilder(PagerDutyEventClient.clientBuilder());
  }

  public PagerDutyEventClient.Builder createClientBuilder(final PagerDutyEventClient.Builder builder) {
    if (endpoint != null) {
      builder.endpoint(endpoint);
    }
    if (requestTimeout != null) {
      builder.requestTimeout(requestTimeout);
    }
    builder.defaultRoutingKey(routingKey);
    if (authToken != null) {
      builder.authToken(authToken);
    }
    return builder;
  }

  public static PagerDutyConfig parseConfig(final Properties properties) {
    return parseConfig(properties, null);
  }

  public static PagerDutyConfig parseConfig(final Properties properties, final String prefix) {
    final var parser = new Parser(prefix);
    parser.parseConfig(properties);
    return parser.createConfig();
  }

  public static PagerDutyConfig parseConfig(final JsonIterator ji) {
    final var parser = new Parser(null);
    ji.testObject(parser);
    return parser.createConfig();
  }

  public String routingKey() {
    return routingKey;
  }

  public String authToken() {
    return authToken;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    private String routingKey;
    private String authToken;

    private Parser(final String prefix) {
      super(prefix);
    }

    private PagerDutyConfig createConfig() {
      if (routingKey == null || routingKey.isBlank()) {
        throw new IllegalStateException("PagerdutyConfig routingKey is required.");
      }
      return new PagerDutyConfig(endpoint, requestTimeout, routingKey, authToken);
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.routingKey = properties.getProperty(prefix + "routingKey");
      this.authToken = properties.getProperty(prefix + "authToken");
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("routingKey", buf, offset, len)) {
        this.routingKey = ji.readString();
      } else if (fieldEquals("authToken", buf, offset, len)) {
        this.authToken = ji.readString();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
