package software.sava.incident.io.config;

import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.io.alert.IncidentIoAlertEventClient;
import software.sava.incident.io.alert.IncidentIoAlertIncidentClient;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.Properties;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Configuration for an [IncidentIoAlertEventClient] and its provider-neutral
/// [IncidentIoAlertIncidentClient] adapter: the alert source's `token` (required), its
/// `alertSourceConfigId` (required unless `endpoint` is set, which is then the full request
/// URL), the optional `resolveTitle`, and the inherited `endpoint` and `requestTimeout`.
public final class IncidentIoAlertSourceConfig extends HttpApiClientConfig {

  private final String token;
  private final String alertSourceConfigId;
  private final String resolveTitle;

  private IncidentIoAlertSourceConfig(final URI endpoint,
                                      final Duration requestTimeout,
                                      final String token,
                                      final String alertSourceConfigId,
                                      final String resolveTitle) {
    super(endpoint, requestTimeout);
    this.token = token;
    this.alertSourceConfigId = alertSourceConfigId;
    this.resolveTitle = resolveTitle;
  }

  public IncidentIoAlertEventClient.Builder createClientBuilder() {
    return createClientBuilder(IncidentIoAlertEventClient.clientBuilder());
  }

  /// Applies this config to `builder`; a value the config does not set leaves the builder's
  /// own untouched.
  public IncidentIoAlertEventClient.Builder createClientBuilder(final IncidentIoAlertEventClient.Builder builder) {
    if (endpoint != null) {
      builder.endpoint(endpoint);
    }
    if (requestTimeout != null) {
      builder.requestTimeout(requestTimeout);
    }
    if (alertSourceConfigId != null) {
      builder.alertSourceConfigId(alertSourceConfigId);
    }
    // token is validated present at parse time
    return builder.token(token);
  }

  /// Starts a provider-neutral adapter builder over `client`, seeded with this config's
  /// resolve title.
  public IncidentIoAlertIncidentClient.Builder createIncidentClientBuilder(final IncidentIoAlertEventClient client) {
    return IncidentIoAlertIncidentClient.build(client).resolveTitle(resolveTitle);
  }

  public static IncidentIoAlertSourceConfig parseConfig(final Properties properties) {
    return parseConfig(properties, null);
  }

  public static IncidentIoAlertSourceConfig parseConfig(final Properties properties, final String prefix) {
    final var parser = new Parser(prefix);
    parser.parseConfig(properties);
    return parser.createConfig();
  }

  public static IncidentIoAlertSourceConfig parseConfig(final JsonIterator ji) {
    return ji.parseObject(new Parser(null), Parser::createConfig);
  }

  public String token() {
    return token;
  }

  /// Null when unset or blank.
  public String alertSourceConfigId() {
    return alertSourceConfigId;
  }

  /// As configured; the adapter treats a blank one as unset.
  public String resolveTitle() {
    return resolveTitle;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    private String token;
    private String alertSourceConfigId;
    private String resolveTitle;

    private Parser(final String prefix) {
      super(prefix);
    }

    private IncidentIoAlertSourceConfig createConfig() {
      if (token == null || token.isBlank()) {
        throw new IllegalStateException("IncidentIoAlertSourceConfig token is required.");
      }
      final var id = alertSourceConfigId == null || alertSourceConfigId.isBlank() ? null : alertSourceConfigId;
      if (id == null && endpoint == null) {
        throw new IllegalStateException(
            "IncidentIoAlertSourceConfig alertSourceConfigId is required unless endpoint is set."
        );
      }
      return new IncidentIoAlertSourceConfig(endpoint, requestTimeout, token, id, resolveTitle);
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.token = properties.getProperty(prefix + "token");
      this.alertSourceConfigId = properties.getProperty(prefix + "alertSourceConfigId");
      this.resolveTitle = properties.getProperty(prefix + "resolveTitle");
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("token", buf, offset, len)) {
        this.token = ji.readString();
      } else if (fieldEquals("alertSourceConfigId", buf, offset, len)) {
        this.alertSourceConfigId = ji.readString();
      } else if (fieldEquals("resolveTitle", buf, offset, len)) {
        this.resolveTitle = ji.readString();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
