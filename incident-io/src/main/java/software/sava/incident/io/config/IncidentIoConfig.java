package software.sava.incident.io.config;

import software.sava.incident.core.api.IncidentSeverity;
import software.sava.incident.core.config.HttpApiClientConfig;
import software.sava.incident.io.CreateIncidentRequest;
import software.sava.incident.io.IncidentIoClient;
import software.sava.incident.io.IncidentIoIncidentClient;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.FieldMatcher;
import systems.comodal.jsoniter.JsonIterator;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Configuration for the native [IncidentIoClient] plus the workspace-specific mapping the
/// provider-neutral [IncidentIoIncidentClient] adapter needs: `severityIds` keyed by
/// [IncidentSeverity] name, and default `incidentTypeId`, `statusId`, `visibility`, and
/// `mode`. The mapping fields are optional at parse time; `visibility` is required to
/// [#createIncidentClientBuilder(IncidentIoClient)]-create a neutral client.
public final class IncidentIoConfig extends HttpApiClientConfig {

  private final String bearerToken;
  private final Map<IncidentSeverity, String> severityIds;
  private final String incidentTypeId;
  private final String statusId;
  private final CreateIncidentRequest.Visibility visibility;
  private final CreateIncidentRequest.Mode mode;

  private IncidentIoConfig(final URI endpoint,
                           final Duration requestTimeout,
                           final String bearerToken,
                           final Map<IncidentSeverity, String> severityIds,
                           final String incidentTypeId,
                           final String statusId,
                           final CreateIncidentRequest.Visibility visibility,
                           final CreateIncidentRequest.Mode mode) {
    super(endpoint, requestTimeout);
    this.bearerToken = bearerToken;
    this.severityIds = severityIds;
    this.incidentTypeId = incidentTypeId;
    this.statusId = statusId;
    this.visibility = visibility;
    this.mode = mode;
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
    // bearerToken is validated present at parse time
    return builder.bearerToken(bearerToken);
  }

  /// Starts a provider-neutral adapter builder over `client`, seeded with this config's
  /// severity id mapping and default type/status/visibility/mode.
  public IncidentIoIncidentClient.Builder createIncidentClientBuilder(final IncidentIoClient client) {
    return IncidentIoIncidentClient.build(client)
        .severityIds(severityIds)
        .incidentTypeId(incidentTypeId)
        .statusId(statusId)
        .visibility(visibility)
        .mode(mode);
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

  public Map<IncidentSeverity, String> severityIds() {
    return severityIds;
  }

  public String incidentTypeId() {
    return incidentTypeId;
  }

  public String statusId() {
    return statusId;
  }

  public CreateIncidentRequest.Visibility visibility() {
    return visibility;
  }

  public CreateIncidentRequest.Mode mode() {
    return mode;
  }

  private static final class Parser extends HttpApiClientConfig.Parser {

    /// Case-insensitive enum-value matcher that fails loudly: a config with an unknown
    /// value should error at parse time, not surface later as a silently absent mapping.
    private static <E extends Enum<E>> CharBufferFunction<E> strictMatcher(final E[] values, final String what) {
      final var matcher = FieldMatcher.enumMatcherIgnoreCase(values);
      return (buf, offset, len) -> {
        final var value = matcher.apply(buf, offset, len);
        if (value == null) {
          throw new IllegalStateException("Unknown " + what + " '" + new String(buf, offset, len) + "'.");
        }
        return value;
      };
    }

    private static final CharBufferFunction<IncidentSeverity> SEVERITY_PARSER =
        strictMatcher(IncidentSeverity.values(), "IncidentSeverity");
    private static final CharBufferFunction<CreateIncidentRequest.Visibility> VISIBILITY_PARSER =
        strictMatcher(CreateIncidentRequest.Visibility.values(), "visibility");
    private static final CharBufferFunction<CreateIncidentRequest.Mode> MODE_PARSER =
        strictMatcher(CreateIncidentRequest.Mode.values(), "mode");

    private static <E> E parseEnum(final CharBufferFunction<E> parser, final String value) {
      if (value == null || value.isBlank()) {
        return null;
      }
      final var trimmed = value.trim();
      return parser.apply(trimmed.toCharArray(), 0, trimmed.length());
    }

    private String bearerToken;
    private final Map<IncidentSeverity, String> severityIds;
    private String incidentTypeId;
    private String statusId;
    private CreateIncidentRequest.Visibility visibility;
    private CreateIncidentRequest.Mode mode;

    private Parser(final String prefix) {
      super(prefix);
      this.severityIds = new EnumMap<>(IncidentSeverity.class);
    }

    private IncidentIoConfig createConfig() {
      if (bearerToken == null || bearerToken.isBlank()) {
        throw new IllegalStateException("IncidentIoConfig bearerToken is required.");
      }
      return new IncidentIoConfig(
          endpoint,
          requestTimeout,
          bearerToken,
          Map.copyOf(severityIds),
          incidentTypeId,
          statusId,
          visibility,
          mode
      );
    }

    @Override
    protected void parseConfig(final Properties properties) {
      super.parseConfig(properties);
      this.bearerToken = properties.getProperty(prefix + "bearerToken");
      for (final var severity : IncidentSeverity.values()) {
        final var severityId = properties.getProperty(prefix + "severityIds." + severity.name());
        if (severityId != null && !severityId.isBlank()) {
          severityIds.put(severity, severityId);
        }
      }
      this.incidentTypeId = properties.getProperty(prefix + "incidentTypeId");
      this.statusId = properties.getProperty(prefix + "statusId");
      this.visibility = parseEnum(VISIBILITY_PARSER, properties.getProperty(prefix + "visibility"));
      this.mode = parseEnum(MODE_PARSER, properties.getProperty(prefix + "mode"));
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("bearerToken", buf, offset, len)) {
        this.bearerToken = ji.readString();
      } else if (fieldEquals("severityIds", buf, offset, len)) {
        ji.readMap(severityIds, SEVERITY_PARSER, (severity, sevJi) -> sevJi.readString());
      } else if (fieldEquals("incidentTypeId", buf, offset, len)) {
        this.incidentTypeId = ji.readString();
      } else if (fieldEquals("statusId", buf, offset, len)) {
        this.statusId = ji.readString();
      } else if (fieldEquals("visibility", buf, offset, len)) {
        this.visibility = ji.applyChars(VISIBILITY_PARSER);
      } else if (fieldEquals("mode", buf, offset, len)) {
        this.mode = ji.applyChars(MODE_PARSER);
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
