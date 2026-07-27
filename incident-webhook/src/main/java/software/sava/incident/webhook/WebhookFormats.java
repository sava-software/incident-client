package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.json.Rfc3339;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Collectors;

import static software.sava.incident.core.json.JsonUtil.escapeJson;

/// Built-in [WebhookFormat] implementations. Both are deliberately plain — no Slack
/// blocks, no templating — so the rendered message stays predictable and every escaping
/// boundary stays testable; a receiver that needs a product's rich features deserves that
/// product's real client.
public enum WebhookFormats implements WebhookFormat {

  /// A canonical JSON document of the alert itself, for receivers that do their own
  /// mapping (custom services, Zapier, n8n, Slack workflow webhooks):
  ///
  /// ```json
  /// {"summary":"...","severity":"CRITICAL","key":"...","source":"...",
  ///  "timestamp":"2026-07-26T12:30:45Z","details":"...","customDetails":{...}}
  /// ```
  ///
  /// `summary` and `severity` are always present; blank optional fields are omitted, and
  /// the timestamp is RFC 3339. Custom detail numbers and booleans keep their JSON types;
  /// any other value serializes as its escaped `toString`.
  GENERIC_JSON,

  /// A [Slack incoming-webhook](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks)
  /// message, `{"text":"..."}`, rendering the alert as plain multi-line text:
  ///
  /// ```
  /// [CRITICAL] summary
  /// details
  /// Source: source
  /// Time: 2026-07-26T12:30:45Z
  /// Key: key
  /// customField: value
  /// ```
  ///
  /// `&`, `<`, and `>` are escaped to `&amp;`/`&lt;`/`&gt;` as the Slack text contract
  /// requires; no other markup is emitted.
  SLACK_TEXT;

  @Override
  public String render(final IncidentAlert alert) {
    return switch (this) {
      case GENERIC_JSON -> renderGenericJson(alert);
      case SLACK_TEXT -> renderSlackText(alert);
    };
  }

  private static void appendString(final StringBuilder json, final String field, final String str) {
    if (str != null && !str.isBlank()) {
      json.append(",\"").append(field).append("\":\"").append(escapeJson(str)).append('"');
    }
  }

  // Numbers and booleans keep their JSON types — BigDecimal/BigInteger as plain
  // (non-scientific) numerals — and anything else is data, not display: it serializes as
  // its escaped toString, newlines surviving as \n escapes.
  private static String toJson(final Map<String, Object> object) {
    return object.entrySet().stream().map(entry -> {
      final var rawVal = switch (entry.getValue()) {
        case null -> "null";
        case BigDecimal bigDecimal -> bigDecimal.toPlainString();
        case BigInteger bigInteger -> bigInteger.toString();
        case Number number -> number.toString();
        case Boolean bool -> bool.toString();
        case Object obj -> '"' + escapeJson(obj.toString()) + '"';
      };
      return String.format("""
          "%s":%s""", escapeJson(entry.getKey()), rawVal);
    }).collect(Collectors.joining(",", "{", "}"));
  }

  static String renderGenericJson(final IncidentAlert alert) {
    final var json = new StringBuilder(1_024);
    json.append(String.format("""
            {"summary":"%s","severity":"%s\"""",
        escapeJson(alert.summary()), alert.severity()
    ));
    appendString(json, "key", alert.key());
    appendString(json, "source", alert.source());
    final var timestamp = alert.timestamp();
    if (timestamp != null) {
      json.append(",\"timestamp\":\"").append(Rfc3339.format(timestamp)).append('"');
    }
    appendString(json, "details", alert.details());
    final var customDetails = alert.customDetails();
    if (customDetails != null && !customDetails.isEmpty()) {
      json.append(",\"customDetails\":").append(toJson(customDetails));
    }
    return json.append('}').toString();
  }

  static String renderSlackText(final IncidentAlert alert) {
    return "{\"text\":\"" + escapeJson(renderText(alert)) + "\"}";
  }

  /// The plain-text rendering inside [#SLACK_TEXT], package-private for direct tests.
  /// Slack's text contract requires `&`, `<`, and `>` escaped as HTML entities; the
  /// transform is applied to the assembled text, labels included, so no raw form survives.
  static String renderText(final IncidentAlert alert) {
    final var text = new StringBuilder(1_024);
    text.append('[').append(alert.severity()).append("] ").append(alert.summary());
    final var details = alert.details();
    if (details != null && !details.isBlank()) {
      text.append('\n').append(details);
    }
    final var source = alert.source();
    if (source != null && !source.isBlank()) {
      text.append("\nSource: ").append(source);
    }
    final var timestamp = alert.timestamp();
    if (timestamp != null) {
      text.append("\nTime: ").append(Rfc3339.format(timestamp));
    }
    final var key = alert.key();
    if (key != null && !key.isBlank()) {
      text.append("\nKey: ").append(key);
    }
    final var customDetails = alert.customDetails();
    if (customDetails != null) {
      for (final var entry : customDetails.entrySet()) {
        text.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
      }
    }
    return text.toString()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
