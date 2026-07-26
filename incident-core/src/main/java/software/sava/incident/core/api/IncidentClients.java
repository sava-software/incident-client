package software.sava.incident.core.api;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;
import java.util.Properties;
import java.util.ServiceLoader;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

/// Provider-neutral entry point: creates an [IncidentClient] from configuration alone, so
/// service-level code can switch providers without referencing provider classes. Providers
/// are discovered via `ServiceLoader`; having a provider module (`incident-pagerduty`,
/// `incident-io`, ...) on the module or class path makes its `provider` id available.
///
/// Properties config selects the provider with a `provider` entry alongside that
/// provider's own properties, all sharing the same prefix:
///
/// ```
/// incident.provider=pagerduty
/// incident.routingKey=...
/// ```
///
/// JSON config wraps the provider's config object, with `provider` first:
///
/// ```json
/// {"provider": "pagerduty", "config": {"routingKey": "..."}}
/// ```
public final class IncidentClients {

  public static IncidentClient createClient(final Properties properties) {
    return createClient(properties, null);
  }

  public static IncidentClient createClient(final Properties properties, final String prefix) {
    final String normalized;
    if (prefix == null || prefix.isBlank()) {
      normalized = "";
    } else {
      normalized = prefix.endsWith(".") ? prefix : prefix + '.';
    }
    return loadFactory(properties.getProperty(normalized + "provider")).createClient(properties, prefix);
  }

  public static IncidentClient createClient(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    if (parser.client == null) {
      throw new IllegalStateException("IncidentClients config 'config' object is required.");
    }
    return parser.client;
  }

  /// Resolves the [IncidentClientFactory] whose [IncidentClientFactory#provider()] matches
  /// `provider` ignoring case and any characters other than letters and digits.
  public static IncidentClientFactory loadFactory(final String provider) {
    if (provider == null || provider.isBlank()) {
      throw new IllegalStateException("IncidentClients config 'provider' is required.");
    }
    final var key = normalize(provider);
    final var available = new ArrayList<String>();
    for (final var factory : ServiceLoader.load(IncidentClientFactory.class)) {
      final var id = factory.provider();
      if (normalize(id).equals(key)) {
        return factory;
      }
      available.add(id);
    }
    throw new IllegalStateException(String.format(
        "No IncidentClientFactory found for provider '%s'. Available providers: %s. Is the provider module on the module or class path?",
        provider, available
    ));
  }

  private static String normalize(final String provider) {
    final int len = provider.length();
    final var normalized = new StringBuilder(len);
    for (int i = 0; i < len; ++i) {
      final char c = provider.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        normalized.append(Character.toLowerCase(c));
      }
    }
    return normalized.toString();
  }

  private static final class Parser implements FieldBufferPredicate {

    private String provider;
    private IncidentClient client;

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("provider", buf, offset, len)) {
        this.provider = ji.readString();
      } else if (fieldEquals("config", buf, offset, len)) {
        if (provider == null) {
          throw new IllegalStateException("IncidentClients config 'provider' must precede 'config'.");
        }
        this.client = loadFactory(provider).createClient(ji);
      } else {
        throw new IllegalStateException("Unknown IncidentClients config field " + new String(buf, offset, len));
      }
      return true;
    }
  }

  private IncidentClients() {
  }
}
