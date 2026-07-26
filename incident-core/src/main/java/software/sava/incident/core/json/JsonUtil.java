package software.sava.incident.core.json;

import systems.comodal.jsoniter.JIUtil;

/// Null-tolerant front for [JIUtil#escapeJson(String)]: the library's contract makes null
/// handling the caller's choice, and this codebase's choice is the empty string — request
/// writers interpolate optional fields without per-site null guards.
public final class JsonUtil {

  /// Escapes `str` for use inside a JSON string literal per RFC 8259; see
  /// [JIUtil#escapeJson(String)]. A null input returns the empty string.
  public static String escapeJson(final String str) {
    return str == null ? "" : JIUtil.escapeJson(str);
  }

  /// Same contract as [#escapeJson(String)], except line feeds and carriage returns are
  /// removed instead of escaped, for API fields rendered on a single line. Stripping
  /// before escaping is equivalent: a newline never influences how its neighbors escape.
  public static String escapeJsonRemoveNewLines(final String str) {
    return str == null ? "" : JIUtil.escapeJson(str.replace("\n", "").replace("\r", ""));
  }

  private JsonUtil() {
  }
}
