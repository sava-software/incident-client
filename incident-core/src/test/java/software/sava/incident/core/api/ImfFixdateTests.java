package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ImfFixdateTests {

  /// Every day name, on the days of one week.
  @Test
  void everyDayNameIsRead() {
    final var days = Map.of(
        "Mon, 09 Nov 2026 10:00:30 GMT", "2026-11-09T10:00:30Z",
        "Tue, 10 Nov 2026 10:00:30 GMT", "2026-11-10T10:00:30Z",
        "Wed, 11 Nov 2026 10:00:30 GMT", "2026-11-11T10:00:30Z",
        "Thu, 12 Nov 2026 10:00:30 GMT", "2026-11-12T10:00:30Z",
        "Fri, 13 Nov 2026 10:00:30 GMT", "2026-11-13T10:00:30Z",
        "Sat, 14 Nov 2026 10:00:30 GMT", "2026-11-14T10:00:30Z",
        "Sun, 15 Nov 2026 10:00:30 GMT", "2026-11-15T10:00:30Z");
    days.forEach((value, instant) -> assertEquals(Instant.parse(instant), ImfFixdate.parse(value), value));
  }

  /// Every month name, on the first of each month of one year.
  @Test
  void everyMonthNameIsRead() {
    final var months = Map.ofEntries(
        Map.entry("Thu, 01 Jan 2026 00:00:00 GMT", "2026-01-01T00:00:00Z"),
        Map.entry("Sun, 01 Feb 2026 00:00:00 GMT", "2026-02-01T00:00:00Z"),
        Map.entry("Sun, 01 Mar 2026 00:00:00 GMT", "2026-03-01T00:00:00Z"),
        Map.entry("Wed, 01 Apr 2026 00:00:00 GMT", "2026-04-01T00:00:00Z"),
        Map.entry("Fri, 01 May 2026 00:00:00 GMT", "2026-05-01T00:00:00Z"),
        Map.entry("Mon, 01 Jun 2026 00:00:00 GMT", "2026-06-01T00:00:00Z"),
        Map.entry("Wed, 01 Jul 2026 00:00:00 GMT", "2026-07-01T00:00:00Z"),
        Map.entry("Sat, 01 Aug 2026 00:00:00 GMT", "2026-08-01T00:00:00Z"),
        Map.entry("Tue, 01 Sep 2026 00:00:00 GMT", "2026-09-01T00:00:00Z"),
        Map.entry("Thu, 01 Oct 2026 00:00:00 GMT", "2026-10-01T00:00:00Z"),
        Map.entry("Sun, 01 Nov 2026 00:00:00 GMT", "2026-11-01T00:00:00Z"),
        Map.entry("Tue, 01 Dec 2026 00:00:00 GMT", "2026-12-01T00:00:00Z"));
    months.forEach((value, instant) -> assertEquals(Instant.parse(instant), ImfFixdate.parse(value), value));
  }

  /// The edges of each field, and a real leap day.
  @Test
  void theEdgesOfEachFieldAreRead() {
    assertEquals(Instant.parse("2026-11-12T23:59:59Z"), ImfFixdate.parse("Thu, 12 Nov 2026 23:59:59 GMT"));
    assertEquals(Instant.parse("2026-11-12T00:00:00Z"), ImfFixdate.parse("Thu, 12 Nov 2026 00:00:00 GMT"));
    assertEquals(Instant.parse("2026-12-31T10:00:30Z"), ImfFixdate.parse("Thu, 31 Dec 2026 10:00:30 GMT"));
    assertEquals(Instant.parse("2024-02-29T10:00:30Z"), ImfFixdate.parse("Thu, 29 Feb 2024 10:00:30 GMT"));
    assertEquals(Instant.parse("1994-11-06T08:49:37Z"), ImfFixdate.parse("Sun, 06 Nov 1994 08:49:37 GMT"));
  }

  /// Text around the date is not an IMF-fixdate. A header value never carries it, since
  /// `HttpHeaders` trims surrounding whitespace, so it is checked here.
  @Test
  void surroundingTextIsNull() {
    for (final var value : List.of(
        "Thu, 12 Nov 2026 10:00:30 GMT ",
        " Thu, 12 Nov 2026 10:00:30 GMT",
        "Thu, 12 Nov 2026 10:00:30 GMT\t",
        "Thu, 12 Nov 2026 10:00:30 GMTX",
        "Thu, 12 Nov 2026 10:00:30 GMT, Fri, 13 Nov 2026 10:00:30 GMT",
        "Thu, 12 Nov 2026 10:00:30",
        "Thu, 12 Nov 2026 10:00:30 GM",
        "")) {
      assertNull(ImfFixdate.parse(value), value);
    }
    assertNull(ImfFixdate.parse(null));
  }

  /// Each number has exactly its width: one digit fewer or one more is null, though the value
  /// it would spell exists.
  @Test
  void everyNumberHasItsFixedWidth() {
    for (final var value : List.of(
        "Mon, 2 Nov 2026 10:00:30 GMT",
        "Thu, 012 Nov 2026 10:00:30 GMT",
        "Thu, 12 Nov 2026 9:00:30 GMT",
        "Thu, 12 Nov 2026 010:00:30 GMT",
        "Thu, 12 Nov 2026 10:0:30 GMT",
        "Thu, 12 Nov 2026 10:000:30 GMT",
        "Thu, 12 Nov 2026 10:00:3 GMT",
        "Thu, 12 Nov 2026 10:00:030 GMT")) {
      assertNull(ImfFixdate.parse(value), value);
    }
  }

  /// Digits other than ASCII ones, a sign on the year, and full or upper-case names are null.
  @Test
  void otherDigitsSignsAndNamesAreNull() {
    for (final var value : List.of(
        "Thu, ١٢ Nov 2026 10:00:30 GMT",
        "Thu, 12 Nov ２０２６ 10:00:30 GMT",
        "Thu, 12 Nov +2026 10:00:30 GMT",
        "Thu, 12 Nov -026 10:00:30 GMT",
        // four digits and the right weekday for that year: the sign refuses it, and the year
        // bound would too
        "Tue, 12 Nov -2026 10:00:30 GMT",
        "Thursday, 12 Nov 2026 10:00:30 GMT",
        "Thu, 12 November 2026 10:00:30 GMT",
        "THU, 12 NOV 2026 10:00:30 GMT",
        "Thu, 12 Nov 2026 10:00:30 Gmt")) {
      assertNull(ImfFixdate.parse(value), value);
    }
  }

  /// RFC 9110 section 5.6.7 gives the year the meaning RFC 5322 section 3.3 gives it, 1900 or
  /// later: the first second of 1900 is read, the last second of 1899 and an earlier year
  /// are null, each with the weekday that is right for its date, so only the year refuses it.
  @Test
  void aYearBefore1900IsNull() {
    assertEquals(Instant.parse("1900-01-01T00:00:00Z"), ImfFixdate.parse("Mon, 01 Jan 1900 00:00:00 GMT"));
    assertNull(ImfFixdate.parse("Sun, 31 Dec 1899 23:59:59 GMT"));
    assertNull(ImfFixdate.parse("Wed, 01 Jan 1000 00:00:00 GMT"));
    assertNull(ImfFixdate.parse("Sat, 01 Jan 0000 00:00:00 GMT"));
  }
}
