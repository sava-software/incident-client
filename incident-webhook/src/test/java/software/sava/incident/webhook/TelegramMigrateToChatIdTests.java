package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// [TelegramTextFormat#botApiMigrateToChatId(String)], the Bot API error body reader behind
/// [TelegramTextFormat#movedTo(String)].
final class TelegramMigrateToChatIdTests {

  /// The Bot API's 400 for an upgraded basic group, in its server's field order (`Query.cpp`
  /// writes `ok`, `error_code`, `description`, `parameters`; `Client.cpp`'s
  /// `check_chat_access` gives the description and the parameter).
  private static final String UPGRADED = """
      {"ok":false,"error_code":400,"description":"Bad Request: group chat was upgraded to a supergroup chat","parameters":{"migrate_to_chat_id":-1001234567890}}""";

  private static long migrateToChatId(final String body) {
    return TelegramTextFormat.botApiMigrateToChatId(body);
  }

  /// An `ok: false` body whose `parameters` object is `parameters`.
  private static long migrateToChatIdOfParameters(final String parameters) {
    return migrateToChatId("""
        {"ok":false,"error_code":400,"parameters":%s}""".formatted(parameters));
  }

  /// A body whose `migrate_to_chat_id` value is the raw JSON `value`.
  private static long migrateToChatIdOfValue(final String value) {
    return migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":%s}""".formatted(value));
  }

  @Test
  void theBotApiBodyNamesTheSupergroup() {
    assertEquals(-1001234567890L, migrateToChatId(UPGRADED));
  }

  @Test
  void fieldOrderDoesNotMatter() {
    assertEquals(-1001234567890L, migrateToChatId("""
        {"parameters":{"migrate_to_chat_id":-1001234567890},"description":"Bad Request: group chat was upgraded to a supergroup chat","error_code":400,"ok":false}"""));
    assertEquals(-1001234567890L, migrateToChatId("""
        {"parameters":{"migrate_to_chat_id":-1001234567890},"ok":false}"""));
  }

  @Test
  void unknownFieldsAreSkipped() {
    assertEquals(-1001234567890L, migrateToChatId("""
        {"result":{"a":[1,{"b":[]},"}"],"c":null},"ok":false,"list":[[{"ok":true}],{}],\
        "parameters":{"retry_after":35,"nested":{"migrate_to_chat_id":-1009999999999},\
        "migrate_to_chat_id":-1001234567890,"after":[true,false,"x"]},\
        "tail":{"parameters":{"migrate_to_chat_id":-1008888888888}}}"""));
    assertEquals(-1001234567890L, migrateToChatId("""
         { "ok" : false , "parameters" : { "migrate_to_chat_id" : -1001234567890 } } """));
    // a decoy nested in another field is never the id
    assertEquals(0, migrateToChatId("""
        {"ok":false,"description":{"parameters":{"migrate_to_chat_id":-1001234567890}}}"""));
    assertEquals(0, migrateToChatIdOfParameters("""
        {"nested":{"migrate_to_chat_id":-1001234567890}}"""));
  }

  @Test
  void okMustBeTheBooleanFalse() {
    for (final var ok : List.of("true", "\"false\"", "0", "null")) {
      assertEquals(0, migrateToChatId("""
          {"ok":%s,"parameters":{"migrate_to_chat_id":-1001234567890}}""".formatted(ok)), ok);
    }
    assertEquals(0, migrateToChatId("""
        {"parameters":{"migrate_to_chat_id":-1001234567890}}"""));
  }

  @Test
  void parametersMustBeAnObjectHoldingTheId() {
    assertEquals(0, migrateToChatId("""
        {"ok":false,"error_code":400}"""));
    for (final var parameters : List.of("null", "[]", "[{\"migrate_to_chat_id\":-1001234567890}]", "5", "\"x\"",
        "{}", "{\"retry_after\":35}")) {
      assertEquals(0, migrateToChatIdOfParameters(parameters), parameters);
    }
  }

  @Test
  void theIdMustBeASupergroupId() {
    // the ends of the range: supergroup 1, and the 52-bit limit
    assertEquals(-1000000000001L, migrateToChatIdOfValue("-1000000000001"));
    assertEquals(-4503599627370495L, migrateToChatIdOfValue("-4503599627370495"));
    // a 9 after the sign, inside the range
    assertEquals(-9000000000000L, migrateToChatIdOfValue("-9000000000000"));
    for (final var value : List.of(
        // just outside the range: the highest id that is not a supergroup's, and past 52 bits
        "-1000000000000", "-4503599627370496",
        "-1", "0", "1001234567890", "-0", "-01", "-1.0", "-1e3", "-1E3", "\"-1001234567890\"",
        "-9223372036854775809", "-9223372036854775808", "true", "[]", "{}", "null",
        // a leading zero, which Long.parseLong alone would read as an id in range
        "-01000000000001", "-001001234567890",
        // not number tokens, or a token without digits
        "-", "--1001234567890", "-+1001234567890", "+1001234567890",
        // digits outside ASCII after an ASCII sign
        "-١٠٠١٢٣٤٥٦٧٨٩٠")) {
      assertEquals(0, migrateToChatIdOfValue(value), value);
    }
  }

  @Test
  void theLastOccurrenceDecides() {
    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":true,"ok":false,"parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"ok":true,"parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"ok":"false","parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    // an earlier occurrence of another type is skipped, not refused
    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":"false","ok":false,"parameters":{"migrate_to_chat_id":-1001234567890}}"""));

    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":false,"parameters":null,"parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":false,"parameters":[5],"parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":false,"parameters":"5","parameters":{"migrate_to_chat_id":-1001234567890}}"""));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"parameters":{"migrate_to_chat_id":-1001234567890},"parameters":null}"""));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"parameters":{"migrate_to_chat_id":-1001234567890},"parameters":{}}"""));
    assertEquals(-1007777777777L, migrateToChatId("""
        {"ok":false,"parameters":{"migrate_to_chat_id":-1001234567890},"parameters":{"migrate_to_chat_id":-1007777777777}}"""));

    assertEquals(-1001234567890L, migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":"-1007777777777","migrate_to_chat_id":-1001234567890}"""));
    assertEquals(0, migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":-1001234567890,"migrate_to_chat_id":"-1007777777777"}"""));
    // the last token is the one judged: an earlier id in range does not survive a later one out of it
    assertEquals(0, migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":-1001234567890,"migrate_to_chat_id":-1}"""));
    assertEquals(-1001234567890L, migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":-1,"migrate_to_chat_id":-1001234567890}"""));
    assertEquals(-1007777777777L, migrateToChatIdOfParameters("""
        {"migrate_to_chat_id":-1001234567890,"migrate_to_chat_id":-1007777777777}"""));
  }

  /// The two readers share one walker and read their own field of the same body.
  @Test
  void bothParametersAreReadIndependently() {
    final var both = """
        {"ok":false,"error_code":400,"parameters":{"retry_after":35,"migrate_to_chat_id":-1001234567890}}""";
    assertEquals(-1001234567890L, migrateToChatId(both));
    assertEquals(Duration.ofSeconds(35), TelegramTextFormat.botApiRetryAfter(both));
    final var reversed = """
        {"ok":false,"parameters":{"migrate_to_chat_id":-1001234567890,"retry_after":35}}""";
    assertEquals(-1001234567890L, migrateToChatId(reversed));
    assertEquals(Duration.ofSeconds(35), TelegramTextFormat.botApiRetryAfter(reversed));
    // each reader's own field alone
    assertNull(TelegramTextFormat.botApiRetryAfter(UPGRADED));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}"""));
  }

  @Test
  void anythingElseIsZeroAndNothingThrows() {
    for (final var body : List.of(
        "invalid_payload", "", "   ", "<html><body>400 Bad Request</body></html>",
        "null", "-1001234567890", "\"text\"", "[" + UPGRADED + "]",
        UPGRADED.substring(0, UPGRADED.length() - 1),
        UPGRADED.substring(0, UPGRADED.length() - 3),
        "{\"ok\":false,\"parameters\":{\"migrate_to_chat_id\":-1001234567890}",
        // the body must open as an object: json-iterator would read fields after a comma
        ",\"ok\":false,\"parameters\":{\"migrate_to_chat_id\":-1001234567890}}")) {
      assertEquals(0, migrateToChatId(body), body);
    }
  }

  /// `skip()` is iterative in the pinned json-iterator, so nesting depth in an ignored field
  /// cannot overflow the stack.
  @Test
  void deepNestingInAnIgnoredFieldIsSkipped() {
    final var depth = 100_000;
    final var unclosed = "{\"ok\":false,\"x\":" + "[".repeat(depth)
        + ",\"parameters\":{\"migrate_to_chat_id\":-1001234567890}}";
    assertEquals(0, migrateToChatId(unclosed));
    final var closed = "{\"x\":" + "[".repeat(depth) + "]".repeat(depth)
        + ",\"ok\":false,\"parameters\":{\"migrate_to_chat_id\":-1001234567890}}";
    assertEquals(-1001234567890L, migrateToChatId(closed));
  }

  @Test
  void aLargeBodyIsReadWhole() {
    final var megabyte = "a".repeat(1 << 20);
    assertEquals(0, migrateToChatId(megabyte));
    assertEquals(-1001234567890L, migrateToChatId("""
        {"ok":false,"description":"%s","parameters":{"migrate_to_chat_id":-1001234567890}}""".formatted(megabyte)));
    assertEquals(0, migrateToChatId("""
        {"ok":false,"description":"%s","parameters":{"migrate_to_chat_id":-1001234567890}""".formatted(megabyte)));
  }

  @Test
  void aNullBodyIsZero() {
    assertEquals(0, migrateToChatId(null));
  }
}
