package software.sava.incident.webhook;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static software.sava.incident.core.api.IncidentSeverity.INFO;
import static software.sava.incident.core.json.JsonUtil.escapeJson;

/// Jazzer entry point for [TelegramTextFormat#botApiMigrateToChatId(String)], the reader of a
/// Telegram Bot API error body naming the supergroup a basic group was upgraded to, which the
/// receiver controls entirely. Three oracles:
///
/// 1. Garbage in: the whole input, decoded as UTF-8 the way the client decodes a failed
///    response's body, gives 0 or a supergroup's chat id (-4503599627370495 to
///    -1000000000001) and never throws anything; a basic group's format moves to exactly that
///    id, without its topic and with its silent severities, or not at all for 0, and a
///    supergroup's format never moves.
/// 2. Round trip: the first eight bytes, big-endian, are a `long n` and the rest, as UTF-8,
///    a description; the Bot API's 400 body built from them,
///    `{"ok":false,"error_code":400,"description":"...","parameters":{"migrate_to_chat_id":n}}`,
///    gives exactly `n` when it is a supergroup's chat id and 0 otherwise.
/// 3. Differential: the same body with `"retry_after":35` before `migrate_to_chat_id` in
///    `parameters` gives the same id, and [TelegramTextFormat#botApiRetryAfter(String)], which
///    walks the body the same way, reads 35 seconds from it.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test sources.
///
/// Run with `./gradlew :incident-webhook:fuzzMigrateToChatId [-PmaxFuzzTime=<seconds>]`.
public final class WebhookMigrateToChatIdFuzz {

  // written out here rather than read from the format: the oracle states the range itself
  private static final long MIN_SUPERGROUP_CHAT_ID = -4_503_599_627_370_495L;
  private static final long MAX_SUPERGROUP_CHAT_ID = -1_000_000_000_001L;

  private static final TelegramTextFormat BASIC = new TelegramTextFormat("-123456789", 7, EnumSet.of(INFO));
  private static final TelegramTextFormat SUPERGROUP = new TelegramTextFormat("-1001234567890");

  private static boolean isSupergroupChatId(final long id) {
    return id >= MIN_SUPERGROUP_CHAT_ID && id <= MAX_SUPERGROUP_CHAT_ID;
  }

  public static void fuzzerTestOneInput(final byte[] data) {
    final var text = new String(data, UTF_8);
    final long garbage = TelegramTextFormat.botApiMigrateToChatId(text);
    if (garbage != 0 && !isSupergroupChatId(garbage)) {
      throw new AssertionError("an id that is not a supergroup's: " + garbage);
    }
    final var expectedMove = garbage == 0
        ? null
        : new TelegramTextFormat(Long.toString(garbage), 0, EnumSet.of(INFO));
    final var moved = BASIC.movedTo(text);
    if (!Objects.equals(expectedMove, moved)) {
      throw new AssertionError("the basic group moved to " + moved + " where the reader read " + garbage);
    }
    final var supergroupMoved = SUPERGROUP.movedTo(text);
    if (supergroupMoved != null) {
      throw new AssertionError("a supergroup moved to " + supergroupMoved);
    }

    if (data.length < Long.BYTES) {
      return;
    }
    final long n = ByteBuffer.wrap(data, 0, Long.BYTES).getLong();
    final var description = new String(data, Long.BYTES, data.length - Long.BYTES, UTF_8);
    final long expected = isSupergroupChatId(n) ? n : 0;
    // concatenation, not %d: a format renders the default locale's digits
    final var head = "{\"ok\":false,\"error_code\":400,\"description\":\"" + escapeJson(description)
        + "\",\"parameters\":{";
    final var id = "\"migrate_to_chat_id\":" + Long.toString(n) + "}}";
    final var body = head + id;
    final long parsed = TelegramTextFormat.botApiMigrateToChatId(body);
    if (parsed != expected) {
      throw new AssertionError("migrate_to_chat_id " + n + " read as " + parsed + " from: " + body);
    }

    final var both = head + "\"retry_after\":35," + id;
    final long parsedBoth = TelegramTextFormat.botApiMigrateToChatId(both);
    if (parsedBoth != expected) {
      throw new AssertionError("migrate_to_chat_id " + n + " read as " + parsedBoth + " beside retry_after from: " + both);
    }
    final var wait = TelegramTextFormat.botApiRetryAfter(both);
    if (!Duration.ofSeconds(35).equals(wait)) {
      throw new AssertionError("retry_after 35 read as " + wait + " beside migrate_to_chat_id from: " + both);
    }
  }

  private WebhookMigrateToChatIdFuzz() {
  }
}
