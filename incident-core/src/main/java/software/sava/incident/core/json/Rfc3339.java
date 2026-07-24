package software.sava.incident.core.json;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.temporal.ChronoField.*;

public final class Rfc3339 {

  /// RFC 3339 `date-time`. Differs from `OffsetDateTime.toString()` in always printing
  /// the seconds field, which RFC 3339 requires and ISO-8601 omits when zero.
  public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .appendLiteral('T')
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .appendFraction(NANO_OF_SECOND, 0, 9, true)
      .appendOffset("+HH:MM", "Z")
      .toFormatter();

  public static String format(final OffsetDateTime timestamp) {
    return FORMATTER.format(timestamp);
  }

  /// Region-based zones are reduced to their offset; RFC 3339 has no `[Area/City]` form.
  public static String format(final ZonedDateTime timestamp) {
    return FORMATTER.format(timestamp.toOffsetDateTime());
  }

  private Rfc3339() {
  }
}
