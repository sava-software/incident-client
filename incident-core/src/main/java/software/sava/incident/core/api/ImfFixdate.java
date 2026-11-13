package software.sava.incident.core.api;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;

import static java.time.temporal.ChronoField.*;

/// The IMF-fixdate of RFC 9110 section 5.6.7, read strictly:
///
///     day-name "," SP 2DIGIT SP month SP 4DIGIT SP 2DIGIT ":" 2DIGIT ":" 2DIGIT SP "GMT"
///
/// with case-sensitive English three-letter names, fixed widths, the literal `GMT`, a date
/// and time that exist, and a year of 1900 or later (RFC 9110 section 5.6.7 gives the year the
/// meaning RFC 5322 section 3.3 gives it).
/// [IncidentClientException#retryAfter(java.net.http.HttpResponse)] states the rule and its
/// limits for every reader of an HTTP-date here.
final class ImfFixdate {

  /// Names from fixed maps, never from locale data.
  private static final Map<Long, String> DAY_NAMES = Map.of(
      1L, "Mon", 2L, "Tue", 3L, "Wed", 4L, "Thu", 5L, "Fri", 6L, "Sat", 7L, "Sun");

  private static final Map<Long, String> MONTH_NAMES = Map.ofEntries(
      Map.entry(1L, "Jan"), Map.entry(2L, "Feb"), Map.entry(3L, "Mar"), Map.entry(4L, "Apr"),
      Map.entry(5L, "May"), Map.entry(6L, "Jun"), Map.entry(7L, "Jul"), Map.entry(8L, "Aug"),
      Map.entry(9L, "Sep"), Map.entry(10L, "Oct"), Map.entry(11L, "Nov"), Map.entry(12L, "Dec"));

  private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
      .parseCaseSensitive()
      .appendText(DAY_OF_WEEK, DAY_NAMES)
      .appendLiteral(", ")
      .appendValue(DAY_OF_MONTH, 2)
      .appendLiteral(' ')
      .appendText(MONTH_OF_YEAR, MONTH_NAMES)
      .appendLiteral(' ')
      .appendValue(YEAR, 4)
      .appendLiteral(' ')
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)
      .appendLiteral(" GMT")
      .toFormatter(Locale.ROOT)
      .withResolverStyle(ResolverStyle.STRICT)
      .withChronology(IsoChronology.INSTANCE);

  /// The first year an HTTP-date may name (RFC 5322 section 3.3 through RFC 9110 section 5.6.7).
  private static final int FIRST_YEAR = 1900;

  private ImfFixdate() {
  }

  /// `value` as an instant when the whole of it is an IMF-fixdate, otherwise null, a null
  /// `value` included. Never throws.
  static Instant parse(final String value) {
    if (value == null) {
      return null;
    }
    try {
      final var dateTime = LocalDateTime.parse(value, FORMAT);
      return dateTime.getYear() < FIRST_YEAR ? null : dateTime.toInstant(ZoneOffset.UTC);
    } catch (final DateTimeException notAnImfFixdate) {
      return null;
    }
  }
}
