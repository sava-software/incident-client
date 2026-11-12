package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// [TelegramTextFormat#botApiRetryAfter(String)], the Bot API error body reader behind
/// [TelegramTextFormat#retryAfter(String)].
final class TelegramRetryAfterTests {

  /// The Bot API's 429 body exactly as its server writes it.
  private static final String TOO_MANY_REQUESTS = """
      {"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}""";

  private static Duration retryAfter(final String body) {
    return TelegramTextFormat.botApiRetryAfter(body);
  }

  /// An `ok: false` body whose `parameters` object is `parameters`.
  private static Duration retryAfterOfParameters(final String parameters) {
    return retryAfter("""
        {"ok":false,"error_code":429,"parameters":%s}""".formatted(parameters));
  }

  /// A body whose `retry_after` value is the raw JSON `value`.
  private static Duration retryAfterOfValue(final String value) {
    return retryAfterOfParameters("""
        {"retry_after":%s}""".formatted(value));
  }

  @Test
  void theBotApiBodyStatesTheWait() {
    assertEquals(Duration.ofSeconds(35), retryAfter(TOO_MANY_REQUESTS));
  }

  @Test
  void fieldOrderDoesNotMatter() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"parameters":{"retry_after":35},"description":"Too Many Requests: retry after 35","error_code":429,"ok":false}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"parameters":{"retry_after":35},"ok":false}"""));
  }

  @Test
  void unknownFieldsAreSkipped() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"result":{"a":[1,{"b":[]},"}"],"c":null},"ok":false,"list":[[{"ok":true}],{}],\
        "parameters":{"migrate_to_chat_id":-1001234567890,"nested":{"retry_after":1},\
        "retry_after":35,"after":[true,false,"x"]},"tail":{"parameters":{"retry_after":2}}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
         { "ok" : false , "parameters" : { "retry_after" : 35 } } """));
  }

  @Test
  void okMustBeTheBooleanFalse() {
    assertNull(retryAfter("""
        {"ok":true,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":"false","parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":0,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":null,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":falsy,"parameters":{"retry_after":35}}"""));
  }

  @Test
  void parametersMustBeAnObjectHoldingRetryAfter() {
    assertNull(retryAfter("""
        {"ok":false,"error_code":429}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":null}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":[{"retry_after":35}]}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":35}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":"{\\"retry_after\\":35}"}"""));
    assertNull(retryAfterOfParameters("{}"));
    assertNull(retryAfterOfParameters("""
        {"migrate_to_chat_id":-1001234567890}"""));
  }

  @Test
  void retryAfterMustBePositiveAsciiDigits() {
    assertEquals(Duration.ofSeconds(1), retryAfterOfValue("1"));
    assertEquals(Duration.ofSeconds(35), retryAfterOfValue("35"));
    // a 9 and a 0 after the first digit, which a range check on each digit must take
    assertEquals(Duration.ofSeconds(39), retryAfterOfValue("39"));
    assertEquals(Duration.ofSeconds(1999), retryAfterOfValue("1999"));
    assertEquals(Duration.ofSeconds(100), retryAfterOfValue("100"));
    assertEquals(Duration.ofSeconds(Long.MAX_VALUE), retryAfterOfValue("9223372036854775807"));
    for (final var value : List.of(
        "0", "-5", "-0", "35.5", "35.0", "1e2", "35E1", "\"35\"", "null", "true", "false",
        "{}", "[35]", "9223372036854775808", "123456789012345678901234567890",
        // not JSON number tokens
        "035", "00", "+5", "35x", "3 5",
        // digits outside ASCII, which Long.parseLong alone would read: null end to end, as the
        // body is read as bytes and neither value is a number token there
        "3５", "٣٥")) {
      assertNull(retryAfterOfValue(value), value);
    }
  }

  @Test
  void theLastOccurrenceOfAFieldDecides() {
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":true,"ok":false,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"ok":true,"parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"ok":"false","parameters":{"retry_after":35}}"""));
    // an earlier occurrence of another type is skipped, not refused
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":"false","ok":false,"parameters":{"retry_after":35}}"""));

    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":null,"parameters":{"retry_after":35}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":[35],"parameters":{"retry_after":35}}"""));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"parameters":"35","parameters":{"retry_after":35}}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":null}"""));
    assertNull(retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":{}}"""));
    assertEquals(Duration.ofSeconds(7), retryAfter("""
        {"ok":false,"parameters":{"retry_after":35},"parameters":{"retry_after":7}}"""));

    assertEquals(Duration.ofSeconds(35), retryAfterOfParameters("""
        {"retry_after":"7","retry_after":35}"""));
    assertNull(retryAfterOfParameters("""
        {"retry_after":35,"retry_after":"7"}"""));
    assertNull(retryAfterOfParameters("""
        {"retry_after":35,"retry_after":0}"""));
    assertEquals(Duration.ofSeconds(7), retryAfterOfParameters("""
        {"retry_after":35,"retry_after":7}"""));
  }

  @Test
  void anythingElseIsNullAndNothingThrows() {
    for (final var body : List.of(
        "invalid_payload", "", "   ", "<html><body>429 Too Many Requests</body></html>",
        "null", "35", "\"text\"", "[" + TOO_MANY_REQUESTS + "]",
        TOO_MANY_REQUESTS.substring(0, TOO_MANY_REQUESTS.length() - 1),
        TOO_MANY_REQUESTS.substring(0, TOO_MANY_REQUESTS.length() - 3),
        "{\"ok\":false,\"parameters\":{\"retry_after\":35}",
        // the body must open as an object: json-iterator would read fields after a comma
        ",\"ok\":false,\"parameters\":{\"retry_after\":35}}")) {
      assertNull(retryAfter(body), body);
    }
  }

  /// `skip()` is iterative in the pinned json-iterator, so nesting depth in an ignored field
  /// cannot overflow the stack.
  @Test
  void deepNestingInAnIgnoredFieldIsSkipped() {
    final var depth = 100_000;
    final var unclosed = "{\"ok\":false,\"x\":" + "[".repeat(depth) + ",\"parameters\":{\"retry_after\":35}}";
    assertNull(retryAfter(unclosed));
    final var closed = "{\"x\":" + "[".repeat(depth) + "]".repeat(depth)
        + ",\"ok\":false,\"parameters\":{\"retry_after\":35}}";
    assertEquals(Duration.ofSeconds(35), retryAfter(closed));
  }

  @Test
  void aLargeBodyIsReadWhole() {
    final var megabyte = "a".repeat(1 << 20);
    assertNull(retryAfter(megabyte));
    assertEquals(Duration.ofSeconds(35), retryAfter("""
        {"ok":false,"description":"%s","parameters":{"retry_after":35}}""".formatted(megabyte)));
    assertNull(retryAfter("""
        {"ok":false,"description":"%s","parameters":{"retry_after":35}""".formatted(megabyte)));
  }

  @Test
  void aNullBodyIsNull() {
    assertNull(retryAfter(null));
  }

  /// The format's hook is the reader.
  @Test
  void theFormatsHookReadsTheSame() {
    final var format = new TelegramTextFormat("chat-1");
    assertEquals(Duration.ofSeconds(35), format.retryAfter(TOO_MANY_REQUESTS));
    assertEquals(retryAfter(TOO_MANY_REQUESTS), format.retryAfter(TOO_MANY_REQUESTS));
    for (final var body : List.of("invalid_payload", "", "{\"ok\":true}", "[" + TOO_MANY_REQUESTS + "]")) {
      assertNull(format.retryAfter(body), body);
      assertEquals(retryAfter(body), format.retryAfter(body), body);
    }
    assertNull(format.retryAfter(null));
  }
}
