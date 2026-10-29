package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

final class AlertKeysTests {

  private static String sha256(final String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void aNullKeyStaysNull() {
    assertNull(IncidentClient.boundKey(null, 255));
    assertEquals("k", IncidentClient.boundKey("k", 255));
  }

  @Test
  void aKeyWithinTheLimitIsItself() {
    assertEquals("k", IncidentClient.boundKey("k", 255));
    assertEquals("k".repeat(255), IncidentClient.boundKey("k".repeat(255), 255));
    assertEquals("k".repeat(1_000), IncidentClient.boundKey("k".repeat(1_000), Integer.MAX_VALUE));
    assertEquals("k".repeat(1_000), IncidentClient.boundKey("k".repeat(1_000), 0), "no limit");
    assertEquals("k".repeat(1_000), IncidentClient.boundKey("k".repeat(1_000), -1), "no limit");
  }

  @Test
  void aLongerKeyKeepsItsPrefixAndCarriesTheDigestOfTheWhole() throws Exception {
    final var key = "Scope Mappings Refused:" + "k".repeat(300);
    final var bound = IncidentClient.boundKey(key, 255);
    assertEquals(255, bound.length());
    assertEquals(key.substring(0, 190) + ':' + sha256(key), bound);
    assertEquals(bound, IncidentClient.boundKey(bound, 255), "shortening is idempotent, so a resolve by the provider's key is unchanged");
    assertNotEquals(bound, IncidentClient.boundKey(key + "x", 255), "two keys with one prefix stay distinct");
  }

  /// A limit with no room for a prefix is the digest alone; one under the digest is refused,
  /// whatever the key, since shortened keys would keep only part of the digest and could collide.
  @Test
  void aLimitWithNoRoomForAPrefixIsTheDigestAloneAndOneUnderItRefused() throws Exception {
    final var key = "k".repeat(100);
    assertEquals(sha256(key), IncidentClient.boundKey(key, 64));
    assertEquals(sha256(key), IncidentClient.boundKey(key, 65), "no room for a prefix: the digest alone, no stray separator");
    assertEquals("k:" + sha256(key), IncidentClient.boundKey(key, 66), "one character of prefix at 66");
    final var refusal = assertThrows(IllegalArgumentException.class, () -> IncidentClient.boundKey(key, 63));
    assertEquals("A key limit of 63 is under the 64-character digest: declare 64 or more, or none.", refusal.getMessage());
    assertThrows(IllegalArgumentException.class, () -> IncidentClient.boundKey("k", 1), "refused whatever the key");
    assertThrows(IllegalArgumentException.class, () -> IncidentClient.boundKey(null, 10), "refused whatever the key");
  }

  /// The limit counts characters as Java does, UTF-16 units, never fewer than the Unicode
  /// characters a provider counts: 255 two-byte characters pass, 256 are shortened to 255.
  @Test
  void theLimitCountsCharactersAsJavaDoes() throws Exception {
    final var twoByte = "\u00E9";
    assertEquals(2, twoByte.getBytes(StandardCharsets.UTF_8).length);
    final var atTheLimit = twoByte.repeat(255);
    assertEquals(atTheLimit, IncidentClient.boundKey(atTheLimit, 255));
    final var past = atTheLimit + twoByte;
    final var bound = IncidentClient.boundKey(past, 255);
    assertEquals(twoByte.repeat(190) + ':' + sha256(past), bound);
    assertEquals(255, bound.length());
  }

  /// A key that opens with a surrogate pair has no prefix at 66 either: the digest alone, as at
  /// 65, with no stray separator; at 67 the whole pair is the prefix.
  @Test
  void aPrefixTheSurrogateCutEmptiesIsTheDigestAlone() throws Exception {
    final var key = "\uD83E\uDDEA" + "k".repeat(100);
    assertEquals(sha256(key), IncidentClient.boundKey(key, 66));
    assertEquals("\uD83E\uDDEA:" + sha256(key), IncidentClient.boundKey(key, 67));
  }

  /// The prefix never splits a surrogate pair: a cut inside one stops before it.
  @Test
  void thePrefixNeverSplitsASurrogatePair() throws Exception {
    final var key = "x".repeat(189) + "\uD83E\uDDEA" + "y".repeat(100);
    assertTrue(Character.isHighSurrogate(key.charAt(189)), "the cut at 190 would split the pair");
    final var bound = IncidentClient.boundKey(key, 255);
    assertEquals("x".repeat(189) + ':' + sha256(key), bound);
    assertEquals(254, bound.length());
    assertEquals("x".repeat(189) + "\uD83E\uDDEA" + ':' + sha256(key), IncidentClient.boundKey(key, 256), "a cut after the pair keeps it");
  }
}
