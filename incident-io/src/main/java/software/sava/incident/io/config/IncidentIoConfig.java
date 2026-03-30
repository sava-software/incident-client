package software.sava.incident.io.config;

import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.io.IncidentIoClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public final class IncidentIoConfig extends HttpApiClientConfig {

  private final String bearerToken;

  private IncidentIoConfig(final URI endpoint,
                           final Duration requestTimeout,
                           final String bearerToken) {
    super(endpoint, requestTimeout);
    this.bearerToken = bearerToken;
  }

  public IncidentIoClient.Builder createClientBuilder() {
    return createClientBuilder(IncidentIoClient.clientBuilder());
  }

  public IncidentIoClient.Builder createClientBuilder(final IncidentIoClient.Builder builder) {
    if (endpoint != null) {
      builder.endpoint(endpoint);
    }
    if (requestTimeout != null) {
      builder.requestTimeout(requestTimeout);
    }
    if (bearerToken != null) {
      builder.bearerToken(bearerToken);
    }
    return builder;
  }

  public static IncidentIoConfig parseConfig(final Properties properties) {
    return parseConfig(properties, null);
  }

  public static IncidentIoConfig parseConfig(final Properties properties, final String prefix) {
    final var parser = new Parser(prefix);
    parser.parseConfig(properties);
    return parser.createConfig();
  }

  public static IncidentIoConfig parseConfig(final JsonIterator ji) {
    final var parser = new Parser(null);
    ji.testObject(parser);
    return parser.createConfig();
  }

  public String bearerToken() {
    return bearerToken;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    private String bearerToken;

    private Parser(final String prefix) {
      super(prefix);
    }

    private IncidentIoConfig createConfig() {
      if (bearerToken == null || bearerToken.isBlank()) {
        throw new IllegalStateException("IncidentIoConfig bearerToken is required.");
      }
      return new IncidentIoConfig(endpoint, requestTimeout, bearerToken);
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.bearerToken = properties.getProperty(prefix + "bearerToken");
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("bearerToken", buf, offset, len)) {
        this.bearerToken = ji.readString();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
