package software.sava.incident.core.json;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class Rfc3339Tests {

  @Test
  void zeroSecondsArePrinted() {
    // OffsetDateTime.toString() would render "2018-08-01T02:03Z"
    assertEquals("2018-08-01T02:03:00Z", Rfc3339.format(OffsetDateTime.parse("2018-08-01T02:03:00Z")));
    assertEquals("2018-08-01T00:00:00Z", Rfc3339.format(ZonedDateTime.of(2018, 8, 1, 0, 0, 0, 0, UTC)));
  }

  @Test
  void fractionalSecondsAreKept() {
    assertEquals("2018-08-01T02:03:04.5Z", Rfc3339.format(OffsetDateTime.parse("2018-08-01T02:03:04.500Z")));
    assertEquals("2018-08-01T02:03:04.000000001Z",
        Rfc3339.format(ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 1, UTC)));
  }

  @Test
  void offsetsPrintAsHoursMinutes() {
    assertEquals("2018-08-01T02:03:04+05:30",
        Rfc3339.format(OffsetDateTime.of(2018, 8, 1, 2, 3, 4, 0, ZoneOffset.ofHoursMinutes(5, 30))));
    assertEquals("2018-08-01T02:03:04-04:00",
        Rfc3339.format(ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, ZoneOffset.ofHours(-4))));
  }

  @Test
  void regionZoneReducesToOffset() {
    assertEquals("2018-08-01T02:03:00-04:00",
        Rfc3339.format(ZonedDateTime.of(2018, 8, 1, 2, 3, 0, 0, ZoneId.of("America/New_York"))));
    assertEquals("2018-01-15T02:03:00-05:00",
        Rfc3339.format(ZonedDateTime.of(2018, 1, 15, 2, 3, 0, 0, ZoneId.of("America/New_York"))));
  }
}
