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

  private PagerDutyConfig(final URI endpoint,
                          final Duration requestTimeout,
                          final String routingKey) {
    super(endpoint, requestTimeout);
    this.routingKey = routingKey;
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
    return ji.parseObject(new Parser(null), Parser::createConfig);
  }

  public String routingKey() {
    return routingKey;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    private String routingKey;

    private Parser(final String prefix) {
      super(prefix);
    }

    private PagerDutyConfig createConfig() {
      if (routingKey == null || routingKey.isBlank()) {
        throw new IllegalStateException("PagerdutyConfig routingKey is required.");
      }
      return new PagerDutyConfig(endpoint, requestTimeout, routingKey);
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.routingKey = properties.getProperty(prefix + "routingKey");
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("routingKey", buf, offset, len)) {
        this.routingKey = ji.readString();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
