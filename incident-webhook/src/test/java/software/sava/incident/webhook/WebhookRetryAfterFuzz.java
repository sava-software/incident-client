package software.sava.incident.webhook;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static software.sava.incident.core.json.JsonUtil.escapeJson;

/// Jazzer entry point for [TelegramTextFormat#botApiRetryAfter(String)], the reader of a
/// Telegram Bot API error body, which the receiver controls entirely. Two oracles:
///
/// 1. Garbage in: the whole input, decoded as UTF-8 the way the client decodes a failed
///    response's body, gives null or a positive wait and never throws anything, and
///    [TelegramTextFormat#retryAfter(String)] gives the same.
/// 2. Round trip: the first eight bytes, big-endian, are a `long n` and the rest, as UTF-8,
///    a description; the Bot API's 429 body built from them,
///    `{"ok":false,"error_code":429,"description":"...","parameters":{"retry_after":n}}`,
///    gives exactly `n` seconds when `n > 0` and null otherwise (a negative `n` renders with
///    a sign, which is not a wait).
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-webhook:fuzzRetryAfter [-PmaxFuzzTime=<seconds>]`.
public final class WebhookRetryAfterFuzz {

  private static final TelegramTextFormat FORMAT = new TelegramTextFormat("chat-1");

  public static void fuzzerTestOneInput(final byte[] data) {
    final var text = new String(data, UTF_8);
    final var garbage = TelegramTextFormat.botApiRetryAfter(text);
    if (garbage != null && !garbage.isPositive()) {
      throw new AssertionError("a wait that is not positive: " + garbage);
    }
    final var hook = FORMAT.retryAfter(text);
    if (!Objects.equals(garbage, hook)) {
      throw new AssertionError("the format's hook read " + hook + " where the reader read " + garbage);
    }

    if (data.length < Long.BYTES) {
      return;
    }
    final long n = ByteBuffer.wrap(data, 0, Long.BYTES).getLong();
    final var description = new String(data, Long.BYTES, data.length - Long.BYTES, UTF_8);
    // concatenation, not %d: a format renders the default locale's digits
    final var body = "{\"ok\":false,\"error_code\":429,\"description\":\"" + escapeJson(description)
        + "\",\"parameters\":{\"retry_after\":" + Long.toString(n) + "}}";
    final var expected = n > 0 ? Duration.ofSeconds(n) : null;
    final var parsed = TelegramTextFormat.botApiRetryAfter(body);
    if (!Objects.equals(expected, parsed)) {
      throw new AssertionError("retry_after " + n + " read as " + parsed + " from: " + body);
    }
  }

  private WebhookRetryAfterFuzz() {
  }
}
