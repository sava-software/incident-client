package software.sava.incident.core.api;

import software.sava.incident.core.client.HttpApiClient;

import java.util.concurrent.CompletableFuture;

/// Provider-neutral incident reporting. Service-level code written against this interface
/// can switch between providers (PagerDuty, incident.io, ...) via configuration — see
/// [IncidentClients] for the config-driven factory; each provider module supplies an
/// adapter from its native client.
public interface IncidentClient extends HttpApiClient {

  CompletableFuture<IncidentResponse> reportIncident(final IncidentAlert alert);

  /// Whether [#resolveIncident(String)] is supported. Providers without a programmatic
  /// resolve fail the returned future with an UnsupportedOperationException.
  boolean supportsResolve();

  /// The longest correlation key the provider accepts, in characters as Java counts them: UTF-16
  /// units, never fewer than the Unicode characters a provider counts; zero, the default, means
  /// no limit. A longer key is shortened by [#boundKey(String, int)] the same way on every report
  /// and resolve, so a caller may key an alert by whatever identifies its subject and resolve it
  /// by the same key. PagerDuty's `dedup_key` stops at 255 characters, its documentation says;
  /// incident.io and the webhook formats document no limit. A limit
  /// under the 64-character digest would keep only part of it and let two keys collide; no
  /// supported provider has one, and the shortening refuses it. The limit is all a client declares: the shortening is one
  /// scheme, not a client's to vary, and a client wrapping another forwards its limit. A client
  /// with a limit answers a report with the key it sent, so a resolve by the response's key
  /// shortens to the key the provider has.
  default int maxKeyLength() {
    return 0;
  }

  /// `key` as a provider accepting `maxKeyLength` characters takes it, never null for a non-null
  /// key: itself within the limit, else its first characters, never splitting a surrogate pair,
  /// a colon and the SHA-256 of the whole key's UTF-8 bytes in hex, so it stays distinct from
  /// every other well-formed key over the limit (two differing only in an unpaired surrogate,
  /// which UTF-8 cannot carry, digest alike), keeps a readable prefix and is the same on every
  /// report and resolve. A limit with no room for a prefix (65 or less, or 66 when the key opens
  /// with a surrogate pair) is the digest alone; one under the digest's 64 is refused with an
  /// IllegalArgumentException, and zero or less is none.
  /// [IncidentService] applies it with the client's [#maxKeyLength()] before every
  /// report and resolve, and a client with a limit applies it to what it sends, so a key reaches
  /// the provider the same way whichever path it takes.
  static String boundKey(final String key, final int maxKeyLength) {
    return AlertKeys.bound(key, maxKeyLength);
  }

  /// Resolves the incident correlated by `key` — the [IncidentResponse#key()] returned
  /// from [#reportIncident(IncidentAlert)], or the [IncidentAlert#key()] it was reported
  /// with.
  CompletableFuture<IncidentResponse> resolveIncident(final String key);
}
