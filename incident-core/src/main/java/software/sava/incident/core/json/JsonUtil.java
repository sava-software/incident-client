package software.sava.incident.core.json;

public final class JsonUtil {

  /// Escapes `str` for use inside a JSON string literal per RFC 8259: quote, backslash,
  /// the named control escapes, and `\ u00XX` for the remaining characters below U+0020.
  /// A null input returns the empty string.
  public static String escapeJson(final String str) {
    return escape(str, false);
  }

  /// Same contract as [#escapeJson(String)], except line feeds and carriage returns are
  /// removed instead of escaped, for API fields rendered on a single line.
  public static String escapeJsonRemoveNewLines(final String str) {
    return escape(str, true);
  }

  private static String escape(final String str, final boolean removeNewLines) {
    if (str == null) {
      return "";
    }
    final int len = str.length();
    int i = 0;
    while (i < len && !needsEscape(str.charAt(i))) {
      ++i;
    }
    if (i == len) {
      return str;
    }
    final var escaped = new StringBuilder(len + 16).append(str, 0, i);
    for (char c; i < len; ++i) {
      c = str.charAt(i);
      switch (c) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\t' -> escaped.append("\\t");
        case '\n' -> {
          if (!removeNewLines) {
            escaped.append("\\n");
          }
        }
        case '\r' -> {
          if (!removeNewLines) {
            escaped.append("\\r");
          }
        }
        default -> {
          if (c < 0x20) {
            escaped.append("\\u00").append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 0xF, 16));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }

  private static boolean needsEscape(final char c) {
    return c < 0x20 || c == '"' || c == '\\';
  }

  private JsonUtil() {
  }
}
