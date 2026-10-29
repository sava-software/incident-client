package software.sava.incident.core.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// The shortening [IncidentClient#boundKey(String, int)] describes, with the limit in characters
/// as Java counts them: UTF-16 units, never fewer than the Unicode characters a provider counts.
final class AlertKeys {

  private static final int DIGEST_LENGTH = 64;

  private AlertKeys() {
  }

  /// Refuses a limit under the digest: shortened keys would keep only part of it and could
  /// collide, and no supported provider has one. Zero or less is no limit.
  static void checkLimit(final int maxLength) {
    if (maxLength > 0 && maxLength < DIGEST_LENGTH) {
      throw new IllegalArgumentException("A key limit of " + maxLength + " is under the " + DIGEST_LENGTH + "-character digest: declare " + DIGEST_LENGTH + " or more, or none.");
    }
  }

  static String bound(final String key, final int maxLength) {
    checkLimit(maxLength);
    if (key == null || maxLength <= 0 || key.length() <= maxLength) {
      return key;
    }
    final var digest = HexFormat.of().formatHex(sha256(key));
    int cut = maxLength - DIGEST_LENGTH - 1;
    if (cut > 0 && Character.isHighSurrogate(key.charAt(cut - 1))) {
      cut--; // a surrogate pair goes whole or not at all
    }
    return cut <= 0 ? digest : key.substring(0, cut) + ':' + digest;
  }

  /// The digest of the key's UTF-8 bytes, which any script can recompute; an unpaired surrogate,
  /// which UTF-8 cannot carry, is replaced on the way, so two keys differing only there digest
  /// alike.
  private static byte[] sha256(final String key) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("every JVM carries SHA-256", e);
    }
  }
}
