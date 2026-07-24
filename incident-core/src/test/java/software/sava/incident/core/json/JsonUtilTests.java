package software.sava.incident.core.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static software.sava.incident.core.json.JsonUtil.escapeJson;
import static software.sava.incident.core.json.JsonUtil.escapeJsonRemoveNewLines;

final class JsonUtilTests {

  @Test
  void nullAndEmpty() {
    assertEquals("", escapeJson(null));
    assertEquals("", escapeJsonRemoveNewLines(null));
    assertEquals("", escapeJson(""));
    assertEquals("", escapeJsonRemoveNewLines(""));
  }

  @Test
  void unchangedInputReturnsSameInstance() {
    final var plain = "The quick brown fox! ~ é ☃";
    assertSame(plain, escapeJson(plain));
    assertSame(plain, escapeJsonRemoveNewLines(plain));
  }

  @Test
  void namedEscapes() {
    assertEquals("\\\"", escapeJson("\""));
    assertEquals("\\\\", escapeJson("\\"));
    assertEquals("\\b", escapeJson("\b"));
    assertEquals("\\f", escapeJson("\f"));
    assertEquals("\\n", escapeJson("\n"));
    assertEquals("\\r", escapeJson("\r"));
    assertEquals("\\t", escapeJson("\t"));
  }

  @Test
  void controlCharactersEscapeAsUnicode() {
    assertEquals("\\u0000", escapeJson(String.valueOf((char) 0x00)));
    assertEquals("\\u0001", escapeJson(String.valueOf((char) 0x01)));
    assertEquals("\\u000b", escapeJson(String.valueOf((char) 0x0B)));
    assertEquals("\\u001f", escapeJson(String.valueOf((char) 0x1F)));
    // U+0020 and above pass through
    assertEquals(" ", escapeJson(" "));
  }

  @Test
  void alreadyEscapedInputIsEscapedAgain() {
    // a raw backslash-quote pair is data, not an escape sequence
    assertEquals("\\\\\\\"", escapeJson("\\\""));
    assertEquals("\\\\n", escapeJson("\\n"));
  }

  @Test
  void escapesAfterCleanPrefix() {
    assertEquals("prefix \\\"quoted\\\" suffix", escapeJson("prefix \"quoted\" suffix"));
    assertEquals("tail\\n", escapeJson("tail\n"));
    assertEquals("\\ttab first", escapeJson("\ttab first"));
  }

  @Test
  void removeNewLinesDropsOnlyNewLines() {
    assertEquals("ab", escapeJsonRemoveNewLines("a\nb"));
    assertEquals("ab", escapeJsonRemoveNewLines("a\r\nb"));
    assertEquals("a\\tb", escapeJsonRemoveNewLines("a\tb"));
    assertEquals("\\\"ab\\\"", escapeJsonRemoveNewLines("\"a\r\nb\""));
    assertEquals("", escapeJsonRemoveNewLines("\r\n"));
    assertEquals("\\u0001ab", escapeJsonRemoveNewLines((char) 0x01 + "a\nb"));
  }

  @Test
  void escapeJsonKeepsNewLines() {
    assertEquals("a\\nb\\rc", escapeJson("a\nb\rc"));
  }
}
